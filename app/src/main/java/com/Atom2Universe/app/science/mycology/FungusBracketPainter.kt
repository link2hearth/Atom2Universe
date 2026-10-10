package com.Atom2Universe.app.science.mycology

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.RadialGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Les champignons en console sur le bois (pleurote) : un tronc à gauche, des étagères superposées qui en sortent, un pied
 * latéral presque nul, des lames qui descendent jusqu'au bois. Même repère que [SpecimenPainter] (cm, x = 0 au centre
 * de la planche, y = 0 à la ligne du sol, 0,9 cm de bois sous la ligne). `capDiam` = longueur d'une étagère,
 * `capRise` = son épaisseur, `cluster` = nombre d'étagères.
 */
internal class BracketPainter(
    private val c: Canvas,
    private val look: FungusLook,
    private val ox: Float,
    private val oy: Float,
    private val s: Float,
    private val hair: Float,
    private val anchors: MutableMap<Anchor, PointF>
) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val count = look.cluster.coerceIn(1, 3)
    private val width = look.capDiam + BARK
    private val xa = -width / 2f + BARK
    private val spacing = look.capRise * 1.9f + 0.6f
    private val outline = withAlpha(darken(look.capMid, 0.62f), 240)

    private fun px(x: Float) = ox + x * s
    private fun py(y: Float) = oy - y * s
    private fun anchor(a: Anchor, x: Float, y: Float) { anchors[a] = PointF(px(x), py(y)) }
    private fun fill(color: Int) = p.apply { reset(); isAntiAlias = true; style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, w: Float) = p.apply {
        reset(); isAntiAlias = true; style = Paint.Style.STROKE; this.color = color
        strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    companion object {
        /** Largeur du tronc dessiné à gauche, en cm. */
        const val BARK = 1.8f
        const val BASE_Y = 1.0f

        /** Hauteur totale de la planche (au-dessus du sol), en cm. */
        fun totalHeight(look: FungusLook): Float {
            val n = look.cluster.coerceIn(1, 3)
            return BASE_Y + (n - 1) * (look.capRise * 1.9f + 0.6f) + look.capRise * 1.4f + 0.9f
        }
    }

    // une étagère : longueur, épaisseur et hauteur d'attache
    private fun len(i: Int) = look.capDiam * (1f - 0.12f * i)
    private fun thick(i: Int) = look.capRise * (1f - 0.1f * i)
    private fun attachY(i: Int) = BASE_Y + i * spacing
    private fun upper(i: Int, u: Float) = attachY(i) + thick(i) * (1f - u.pow(2.3f)).coerceAtLeast(0f).pow(1f / 2.3f)
    private fun flesh(i: Int, u: Float) = attachY(i) + thick(i) * 0.12f * (1f - 2f * u)
    private fun gillBottom(i: Int, u: Float) = flesh(i, u) - thick(i) * 0.6f * (1f - u.pow(1.6f))
    private fun xAt(i: Int, u: Float) = xa + len(i) * u

    private fun bark() {
        val top = totalHeight(look)
        val rect = RectF(px(-width / 2f), py(top), px(xa), py(-0.9f))
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(rect.left, 0f, rect.right, 0f,
            intArrayOf(0xFF4E3B2B.toInt(), 0xFF6D553F.toInt(), 0xFF5A4533.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        val g = Random(look.seed + 111)
        stroke(withAlpha(0xFF2E2118.toInt(), 150), max(hair, 0.05f * s))
        for (k in 0 until 7) {
            val x = -width / 2f + BARK * (0.1f + 0.8f * k / 6f) + (g.nextFloat() - 0.5f) * 0.12f
            val path = Path()
            var y = top
            path.moveTo(px(x), py(y))
            while (y > -0.9f) {
                y -= 0.5f + g.nextFloat() * 0.6f
                path.lineTo(px(x + (g.nextFloat() - 0.5f) * 0.18f), py(max(y, -0.9f)))
            }
            c.drawPath(path, p)
        }
        stroke(withAlpha(0xFF2E2118.toInt(), 220), hair * 1.1f); c.drawRect(rect, p)
    }

    /** Le contour du dessus du chapeau, de l'attache à la marge. */
    private fun capPath(i: Int): Path {
        val path = Path()
        val n = 36
        for (k in 0..n) {
            val u = k / n.toFloat()
            if (k == 0) path.moveTo(px(xAt(i, u)), py(upper(i, u))) else path.lineTo(px(xAt(i, u)), py(upper(i, u)))
        }
        for (k in n downTo 0) {
            val u = k / n.toFloat()
            path.lineTo(px(xAt(i, u)), py(flesh(i, u)))
        }
        path.close()
        return path
    }

    /** Les lames, en dessous : un coin qui descend jusqu'au bois et s'effile vers la marge. */
    private fun gillPath(i: Int): Path {
        val path = Path()
        val n = 36
        for (k in 0..n) {
            val u = k / n.toFloat()
            if (k == 0) path.moveTo(px(xAt(i, u)), py(flesh(i, u))) else path.lineTo(px(xAt(i, u)), py(flesh(i, u)))
        }
        for (k in n downTo 0) {
            val u = k / n.toFloat()
            path.lineTo(px(xAt(i, u)), py(gillBottom(i, u)))
        }
        path.close()
        return path
    }

    // ------------------------------------------------------------------ vue de côté

    fun side() {
        bark()
        for (i in count - 1 downTo 0) {
            val gills = gillPath(i)
            // lames
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(flesh(i, 0.2f)), 0f, py(gillBottom(i, 0.1f)), darken(look.hymColor, 0.18f), look.hymColor, Shader.TileMode.CLAMP)
            c.drawPath(gills, p)
            c.save(); c.clipPath(gills)
            stroke(withAlpha(darken(look.hymColor, 0.45f), 160), hair * 0.7f)
            val ax = px(xa); val ay = py(flesh(i, 0f) - thick(i) * 0.3f)
            for (k in 0..26) {
                val u = 0.04f + 0.96f * k / 26f
                c.drawLine(ax, ay, px(xAt(i, u)), py(gillBottom(i, u) + (flesh(i, u) - gillBottom(i, u)) * 0.45f), p)
            }
            c.restore()
            stroke(withAlpha(darken(look.hymColor, 0.55f), 210), hair); c.drawPath(gills, p)
            // dessus du chapeau
            val cap = capPath(i)
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(px(xa), 0f, px(xAt(i, 1f)), 0f, look.capCenter, look.capEdge, Shader.TileMode.CLAMP)
            c.drawPath(cap, p)
            c.save(); c.clipPath(cap)
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(attachY(i) + thick(i)), 0f, py(attachY(i) - 0.1f), withAlpha(Color.WHITE, 90), withAlpha(Color.BLACK, 70), Shader.TileMode.CLAMP)
            c.drawRect(RectF(px(xa), py(attachY(i) + thick(i)), px(xAt(i, 1f)), py(attachY(i) - 0.2f)), p)
            c.restore()
            stroke(outline, hair * 1.2f); c.drawPath(cap, p)
            // la marge, claire et fine
            stroke(withAlpha(lighten(look.capEdge, 0.35f), 170), max(hair * 1.4f, 0.04f * s))
            val rim = Path()
            for (k in 14..36) {
                val u = k / 36f
                if (k == 14) rim.moveTo(px(xAt(i, u)), py(upper(i, u) - 0.03f)) else rim.lineTo(px(xAt(i, u)), py(upper(i, u) - 0.03f))
            }
            c.drawPath(rim, p)
        }
        anchor(Anchor.CAP, xAt(0, 0.45f), upper(0, 0.45f) - thick(0) * 0.18f)
        anchor(Anchor.EDGE, xAt(0, 0.99f), flesh(0, 1f) + 0.02f)
        anchor(Anchor.FACE, xAt(0, 0.55f), (flesh(0, 0.55f) + gillBottom(0, 0.55f)) / 2f)
        anchor(Anchor.STIPE_M, xa + 0.35f, flesh(0, 0f) - thick(0) * 0.35f)
        anchor(Anchor.BASE, xa - BARK * 0.5f, BASE_Y * 0.35f)
    }

    // ------------------------------------------------------------------ coupe

    fun section() {
        bark()
        for (i in count - 1 downTo 0) {
            val cap = capPath(i)
            val gills = gillPath(i)
            // lames coupées : une bande de lames verticales
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(flesh(i, 0.2f)), 0f, py(gillBottom(i, 0.1f)), lighten(look.hymInner, 0.12f), darken(look.hymInner, 0.1f), Shader.TileMode.CLAMP)
            c.drawPath(gills, p)
            c.save(); c.clipPath(gills)
            stroke(withAlpha(darken(look.hymInner, 0.4f), 140), hair * 0.6f)
            var x = xa + 0.05f
            while (x < xAt(i, 1f)) {
                val u = (x - xa) / len(i)
                c.drawLine(px(x), py(flesh(i, u) + 0.02f), px(x), py(gillBottom(i, u) - 0.02f), p)
                x += 0.1f
            }
            c.restore()
            stroke(withAlpha(darken(look.hymInner, 0.55f), 210), hair); c.drawPath(gills, p)
            // chair : épaisse près du bois, mince vers la marge
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, py(upper(i, 0f)), 0f, py(flesh(i, 0f)), lighten(look.flesh, 0.1f), look.flesh, Shader.TileMode.CLAMP)
            c.drawPath(cap, p)
            c.save(); c.clipPath(cap)
            stroke(withAlpha(darken(look.flesh, 0.45f), 70), max(hair * 5f, 0.26f * s)); c.drawPath(cap, p)
            c.restore()
            // la peau du dessus
            stroke(withAlpha(look.capMid, 255), max(hair * 2.2f, 0.1f * s))
            val skin = Path()
            for (k in 0..36) {
                val u = k / 36f
                if (k == 0) skin.moveTo(px(xAt(i, u)), py(upper(i, u))) else skin.lineTo(px(xAt(i, u)), py(upper(i, u)))
            }
            c.drawPath(skin, p)
            stroke(outline, hair); c.drawPath(cap, p)
        }
        anchor(Anchor.S_FLESH, xAt(0, 0.22f), (upper(0, 0.22f) + flesh(0, 0.22f)) / 2f)
        anchor(Anchor.S_HYM, xAt(0, 0.5f), (flesh(0, 0.5f) + gillBottom(0, 0.5f)) / 2f)
        anchor(Anchor.S_STIPE, xa + 0.3f, flesh(0, 0f) - thick(0) * 0.3f)
        anchor(Anchor.S_BASE, xa - BARK * 0.45f, attachY(0))
    }

    // ------------------------------------------------------------------ vue de dessous

    /** L'étagère vue d'en dessous : un éventail dont les lames partent toutes du point d'attache, à gauche. */
    fun under(ucx: Float, ucy: Float, ur: Float) {
        val half = 1.62f
        val raw = { t: Float -> cos(PI.toFloat() * t / (2f * half)).coerceAtLeast(0f).pow(0.5f) * (1f + 0.025f * sin(t * 5f + look.seed)) }
        var ymax = 0f
        for (k in 0..60) { val t = -half + 2f * half * k / 60f; ymax = max(ymax, abs(raw(t) * sin(t))) }
        val reach = 0.9f * ur / ymax
        val ax = ucx - 0.55f * ur
        val ay = ucy
        val shape = Path()
        val n = 80
        for (k in 0..n) {
            val t = -half + 2f * half * k / n
            val rr = reach * raw(t)
            val x = ax + rr * cos(t)
            val y = ay + rr * sin(t)
            if (k == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(ax, ay, reach * 1.1f, intArrayOf(darken(look.hymColor, 0.1f), look.hymColor, look.capEdge),
            floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(shape, p)
        c.save(); c.clipPath(shape)
        val line = withAlpha(darken(look.hymColor, 0.42f), 175)
        val edge = withAlpha(lighten(look.hymColor, 0.4f), 130)
        val g = Random(look.seed + 113)
        for (k in 0..46) {
            val a = -half + 2f * half * k / 46f + (g.nextFloat() - 0.5f) * 0.02f
            val short = k % 2 == 1
            val x1 = ax + (if (short) 0.4f else 0.05f) * reach * cos(a)
            val y1 = ay + (if (short) 0.4f else 0.05f) * reach * sin(a)
            val x2 = ax + reach * 1.05f * cos(a)
            val y2 = ay + reach * 1.05f * sin(a)
            stroke(line, max(hair * 0.8f, ur * 0.011f)); c.drawLine(x1, y1, x2, y2, p)
            stroke(edge, hair * 0.5f); c.drawLine(x1 + 2f, y1 + 2f, x2, y2, p)
        }
        c.restore()
        stroke(outline, hair * 1.2f); c.drawPath(shape, p)
        // le pied latéral : un court coin contre le bois, qui s'évase vers l'éventail
        val stub = Path()
        stub.moveTo(ax - 0.1f * ur, ay - 0.07f * ur)
        stub.lineTo(ax + 0.16f * ur, ay - 0.15f * ur)
        stub.lineTo(ax + 0.16f * ur, ay + 0.15f * ur)
        stub.lineTo(ax - 0.1f * ur, ay + 0.07f * ur)
        stub.close()
        c.drawPath(stub, fill(lighten(look.hymColor, 0.2f)))
        stroke(withAlpha(darken(look.hymColor, 0.55f), 220), hair); c.drawPath(stub, p)
        anchors[Anchor.U_RIM] = PointF(ax + reach * 0.62f * cos(-0.8f), ay + reach * 0.62f * sin(-0.8f))
        anchors[Anchor.U_HYM] = PointF(ax + reach * 0.62f * cos(0.5f), ay + reach * 0.62f * sin(0.5f))
        anchors[Anchor.U_CENTER] = PointF(ax, ay)
    }
}
