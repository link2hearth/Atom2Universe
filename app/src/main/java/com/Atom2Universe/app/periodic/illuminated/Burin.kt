package com.Atom2Universe.app.periodic.illuminated

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * L'outil du graveur : traits, hachures, contre-hachures, pointillés et lavis.
 *
 * Tout se dessine en coordonnées de la carte (360 × 520). Les hachures coûtent des centaines de
 * traits : on ne s'en sert que pour ce qui est **cuit une fois** dans une image (la planche
 * fixe, les pièces mobiles), jamais à chaque image de l'animation.
 *
 * [gold] est le seul pinceau qui écrit aussi dans le masque d'or : c'est ce masque qui fait
 * miroiter la feuille d'or quand la lumière passe.
 */
internal class Burin(val c: Canvas, private val goldMask: Canvas? = null) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val random = Random(7)
    private var lines = FloatArray(512)

    fun pen(color: Int, width: Float = 0f): Paint {
        p.reset(); p.isAntiAlias = true; p.color = color
        p.style = if (width > 0f) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        return p
    }

    fun line(x: Float, y: Float, x2: Float, y2: Float, w: Float = 1f, color: Int = Ink.SEPIA) {
        c.drawLine(x, y, x2, y2, pen(color, w))
    }

    fun stroke(path: Path, w: Float = 1.2f, color: Int = Ink.SEPIA) = c.drawPath(path, pen(color, w))
    fun fill(path: Path, color: Int) = c.drawPath(path, pen(color))

    /**
     * Une pièce gravée complète : lavis, ombre en hachures, contour. C'est le geste de base
     * de toutes les scènes.
     */
    fun body(
        shape: Path, wash: Int, tone: Float = 0f, angle: Float = 35f, fade: Fade? = null,
        outline: Float = 1f, washAlpha: Int = 150
    ) {
        if (wash != 0) wash(shape, wash, washAlpha)
        if (tone > 0f) tone(shape, tone, angle, fade = fade)
        if (outline > 0f) stroke(shape, outline)
    }

    /** Lavis : couleur posée à la main sous la gravure, transparente. */
    fun wash(path: Path, color: Int, alpha: Int = 120) = c.drawPath(path, pen(withAlpha(color, alpha)))

    /** Lavis dégradé, plus soutenu d'un côté que de l'autre. */
    fun washGradient(path: Path, color: Int, x0: Float, y0: Float, a0: Int, x1: Float, y1: Float, a1: Int) {
        pen(color).shader = LinearGradient(x0, y0, x1, y1, withAlpha(color, a0), withAlpha(color, a1), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
    }

    fun glow(x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        pen(color).shader = RadialGradient(x, y, r, withAlpha(color, alpha), withAlpha(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }

    /**
     * Hachures parallèles dans [shape]. [fade] donne une direction d'ombre : les traits
     * s'effacent vers la lumière, comme un burin qui relève la pointe.
     */
    fun hatch(
        shape: Path, angle: Float, gap: Float, w: Float = .55f, color: Int = Ink.SEPIA,
        fade: Fade? = null, wobble: Float = .18f
    ) {
        shape.computeBounds(bounds, true)
        c.save(); c.clipPath(shape)
        drawHatch(bounds, angle, gap, w, color, fade, wobble)
        c.restore()
    }

    /** Hachures dans un rectangle, sans découpe. */
    fun hatchRect(r: RectF, angle: Float, gap: Float, w: Float = .55f, color: Int = Ink.SEPIA, fade: Fade? = null) {
        c.save(); c.clipRect(r)
        drawHatch(r, angle, gap, w, color, fade, .18f)
        c.restore()
    }

    /** Ton par contre-hachures : 0 = papier, 1 = noir. */
    fun tone(shape: Path, value: Float, angle: Float = 35f, color: Int = Ink.SEPIA, fade: Fade? = null) {
        if (value <= .05f) return
        val gap = 5.2f - 3.2f * value.coerceAtMost(1f)
        hatch(shape, angle, gap, .5f + .25f * value, color, fade)
        if (value > .45f) hatch(shape, angle + 62f, gap * 1.25f, .45f, color, fade)
        if (value > .8f) hatch(shape, angle - 48f, gap * 1.4f, .4f, color, fade)
    }

    private fun drawHatch(r: RectF, angle: Float, gap: Float, w: Float, color: Int, fade: Fade?, wobble: Float) {
        val a = Math.toRadians(angle.toDouble())
        val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
        val nx = -dy; val ny = dx
        val cx = r.centerX(); val cy = r.centerY()
        val half = hypot(r.width(), r.height()) / 2f + 2f
        val n = (2 * half / gap).toInt() + 1
        if (lines.size < n * 4) lines = FloatArray(n * 4)
        for (i in 0 until n) {
            val o = -half + i * gap + (random.nextFloat() - .5f) * gap * wobble
            val px = cx + nx * o; val py = cy + ny * o
            val l0 = half * (1f - random.nextFloat() * .04f)
            lines[i * 4] = px - dx * l0; lines[i * 4 + 1] = py - dy * l0
            lines[i * 4 + 2] = px + dx * l0; lines[i * 4 + 3] = py + dy * l0
        }
        val paint = pen(color, w)
        paint.strokeCap = Paint.Cap.BUTT
        if (fade != null) paint.shader = LinearGradient(fade.x0, fade.y0, fade.x1, fade.y1,
            withAlpha(color, fade.a0), withAlpha(color, fade.a1), Shader.TileMode.CLAMP)
        c.drawLines(lines, 0, n * 4, paint)
    }

    /** Pointillé : la manière du graveur pour les ombres douces et les terrains. */
    fun stipple(shape: Path, count: Int, r: Float = .55f, color: Int = Ink.SEPIA, seed: Int = 1) {
        shape.computeBounds(bounds, true)
        val rnd = Random(seed)
        c.save(); c.clipPath(shape)
        val paint = pen(color)
        repeat(count) {
            c.drawCircle(bounds.left + rnd.nextFloat() * bounds.width(),
                bounds.top + rnd.nextFloat() * bounds.height(), r * (.6f + rnd.nextFloat() * .7f), paint)
        }
        c.restore()
    }

    /**
     * Trait effilé : épais au milieu, fin aux bouts, comme une taille de burin.
     * La courbe est une cubique de Bézier.
     */
    fun taper(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
        w: Float, color: Int = Ink.SEPIA
    ) {
        val steps = 14
        val left = FloatArray((steps + 1) * 2); val right = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val t = i / steps.toFloat(); val u = 1 - t
            val x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
            val y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
            val tx = 3 * u * u * (x1 - x0) + 6 * u * t * (x2 - x1) + 3 * t * t * (x3 - x2)
            val ty = 3 * u * u * (y1 - y0) + 6 * u * t * (y2 - y1) + 3 * t * t * (y3 - y2)
            val len = hypot(tx, ty).coerceAtLeast(.001f)
            val half = w / 2f * sin(Math.PI * t).toFloat().coerceAtLeast(.12f)
            left[i * 2] = x - ty / len * half; left[i * 2 + 1] = y + tx / len * half
            right[i * 2] = x + ty / len * half; right[i * 2 + 1] = y - tx / len * half
        }
        val path = Path()
        path.moveTo(left[0], left[1])
        for (i in 1..steps) path.lineTo(left[i * 2], left[i * 2 + 1])
        for (i in steps downTo 0) path.lineTo(right[i * 2], right[i * 2 + 1])
        path.close()
        fill(path, color)
    }

    /** Feuille d'or : posée en couleur, et inscrite dans le masque qui la fera miroiter. */
    fun gold(path: Path, shade: Fade? = null) {
        val paint = pen(Gilding.LEAF)
        paint.shader = if (shade != null) LinearGradient(shade.x0, shade.y0, shade.x1, shade.y1,
            intArrayOf(Gilding.LIGHT, Gilding.LEAF, Gilding.DEEP), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        else null
        c.drawPath(path, paint)
        goldMask?.drawPath(path, maskPaint)
    }

    fun goldStroke(path: Path, w: Float) {
        c.drawPath(path, pen(Gilding.LEAF, w))
        maskStroke.strokeWidth = w
        goldMask?.drawPath(path, maskStroke)
    }

    fun goldCircle(x: Float, y: Float, r: Float) {
        pen(Gilding.LEAF).shader = RadialGradient(x - r * .35f, y - r * .4f, r * 1.4f,
            intArrayOf(Gilding.LIGHT, Gilding.LEAF, Gilding.DEEP), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
        goldMask?.drawCircle(x, y, r, maskPaint)
    }

    fun goldText(text: String, x: Float, y: Float, paint: Paint) {
        val color = paint.color
        paint.color = Gilding.LEAF
        c.drawText(text, x, y, paint)
        paint.color = color
        goldMask?.let { m ->
            val old = paint.shader; paint.shader = null
            paint.color = 0xFFFFFFFF.toInt(); m.drawText(text, x, y, paint)
            paint.shader = old; paint.color = color
        }
    }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val maskStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
}

/** Direction d'effacement d'une hachure : opaque en (x0, y0), [a1] en (x1, y1). */
internal class Fade(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val a0: Int = 255, val a1: Int = 0)

internal object Ink {
    const val SEPIA = 0xFF2B1D14.toInt()
    const val BROWN = 0xFF5B3E2A.toInt()
    const val RUBRIC = 0xFFA4281B.toInt()
    const val WHITE = 0xFFFFFBF0.toInt()
}

internal object Gilding {
    const val LIGHT = 0xFFFFF0B0.toInt()
    const val LEAF = 0xFFD8AE4E.toInt()
    const val DEEP = 0xFF8C6421.toInt()
}

internal fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/** Petits constructeurs de chemins, pour que les scènes se lisent comme des plans. */
internal fun poly(vararg xy: Float): Path = Path().apply {
    moveTo(xy[0], xy[1])
    for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
    close()
}

internal fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): Path =
    Path().apply { addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW) }

internal fun rect(l: Float, t: Float, r: Float, b: Float): Path =
    Path().apply { addRect(l, t, r, b, Path.Direction.CW) }

/** Un membre ou un conduit : un tronc de cône arrondi aux deux bouts, de (x0, y0) à (x1, y1). */
internal fun limb(x0: Float, y0: Float, x1: Float, y1: Float, w0: Float, w1: Float): Path {
    val len = hypot(x1 - x0, y1 - y0)
    val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
    val quad = poly(
        x0 + nx * w0 / 2, y0 + ny * w0 / 2, x1 + nx * w1 / 2, y1 + ny * w1 / 2,
        x1 - nx * w1 / 2, y1 - ny * w1 / 2, x0 - nx * w0 / 2, y0 - ny * w0 / 2
    )
    val caps = Path().apply {
        addCircle(x0, y0, w0 / 2, Path.Direction.CW); addCircle(x1, y1, w1 / 2, Path.Direction.CW)
    }
    return Path().apply { op(quad, caps, Path.Op.UNION) }
}
