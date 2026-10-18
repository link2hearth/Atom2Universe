package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Potassium — la banane, le fruit qui en contient le plus. Un bananier dans la plantation :
 * ses grandes feuilles déchirées par le vent se balancent, le régime pend sous son bourgeon
 * violet, et un papillon bleu passe.
 */
internal class ScenePotassium : EngravedScene() {

    override val nameRes = R.string.card_scene_banana_tree
    override val noteRes = R.string.card_note_banana_tree
    override val explainRes = R.string.card_explain_banana_tree

    override fun engrave(b: Burin) {
        plantation(b)
        trunk(b)
    }

    private fun plantation(b: Burin) {
        val hills = Path().apply {
            moveTo(40f, 210f); cubicTo(90f, 186f, 130f, 196f, 180f, 200f)
            cubicTo(230f, 204f, 270f, 182f, 320f, 196f); lineTo(320f, 260f); lineTo(40f, 260f); close()
        }
        b.body(hills, 0xFF8AA88A.toInt(), .25f, 0f, Fade(0f, 186f, 0f, 250f, 40, 180), outline = .6f, washAlpha = 130)
        // D'autres bananiers au loin, en silhouettes.
        val rnd = Random(5)
        for (x in floatArrayOf(58f, 92f, 238f, 276f, 304f)) {
            val base = 252f; val h = 40f + rnd.nextFloat() * 16f
            b.line(x, base, x + 1f, base - h, 2.4f, 0xFF5A6A3A.toInt())
            for (k in 0 until 5) {
                // Une feuille large qui retombe : deux arcs de la base à la pointe, pas un trait.
                val a = -PI.toFloat() * (.1f + k * .2f)
                val len = 18f + rnd.nextFloat() * 8f
                val ex = x + cos(a) * len; val ey = base - h + sin(a) * len * .6f + 7f
                val mx = (x + ex) / 2f; val my = (base - h + ey) / 2f - 7f
                val nx = -(ey - base + h) / len * 4.5f; val ny = (ex - x) / len * 4.5f
                val blade = Path().apply {
                    moveTo(x, base - h); quadTo(mx + nx, my + ny, ex, ey + 3f); quadTo(mx - nx, my - ny, x, base - h); close()
                }
                b.fill(blade, withAlpha(0xFF4E6E3C.toInt(), 215))
            }
        }
        val ground = rect(40f, 252f, 320f, 340f)
        b.body(ground, 0xFF7A8A4A.toInt(), .35f, 0f, Fade(0f, 252f, 0f, 330f, 60, 230), outline = 0f, washAlpha = 220)
        // Des touffes d'herbe au premier plan.
        repeat(40) {
            val x = 42f + rnd.nextFloat() * 276f; val y = 290f + rnd.nextFloat() * 40f
            val h = 5f + rnd.nextFloat() * 8f
            b.taper(x, y, x + 1f, y - h * .5f, x + 2f, y - h * .8f, x + 3f * (rnd.nextFloat() - .4f), y - h, 1.2f, 0xFF3E5A28.toInt())
        }
    }

    /** Le faux tronc : des gaines de feuilles serrées, vert brun, fibreuses. */
    private fun trunk(b: Burin) {
        val stem = Path().apply {
            moveTo(146f, 336f); cubicTo(148f, 280f, 152f, 220f, 156f, 172f)
            lineTo(170f, 172f); cubicTo(170f, 220f, 172f, 280f, 178f, 336f); close()
        }
        b.wash(stem, 0xFF8A9A5A.toInt(), 255)
        b.hatch(stem, 92f, 1.5f, .45f, Ink.SEPIA, Fade(178f, 0f, 160f, 0f, 230, 0))
        b.hatch(stem, 92f, 2.8f, .4f, withAlpha(0xFF5A3A22.toInt(), 200))
        // Les vieilles gaines brunes qui pendent.
        for ((y, side) in listOf(300f to -1f, 262f to 1f, 228f to -1f)) {
            val x = if (side < 0) 148f else 174f
            val sheath = Path().apply { moveTo(x, y - 12f); cubicTo(x + side * 8f, y - 4f, x + side * 10f, y + 10f, x + side * 6f, y + 22f); lineTo(x, y + 8f); close() }
            b.body(sheath, 0xFF8A6A3A.toInt(), .45f, 80f, outline = .6f, washAlpha = 255)
        }
        b.stroke(stem, .9f)
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(LEAF_LONG, RectF(-4f, -46f, 126f, 54f)) { b -> leaf(b, 120f, 25f, 3) },
        Sprite(LEAF_MID, RectF(-4f, -42f, 110f, 48f)) { b -> leaf(b, 104f, 22f, 7) },
        Sprite(LEAF_YOUNG, RectF(-4f, -24f, 90f, 24f)) { b -> leaf(b, 84f, 10f, 11) },
        Sprite(BUNCH, RectF(150f, 160f, 262f, 320f)) { b -> bunch(b) }
    )

    /**
     * Une feuille de bananier, sa base en (0, 0), pointée vers la droite : nervure arquée,
     * limbe à nervures parallèles, et des déchirures que le vent y a faites.
     */
    private fun leaf(b: Burin, len: Float, w: Float, seed: Int) {
        val n = 40
        val mx = FloatArray(n + 1); val my = FloatArray(n + 1)
        val nx = FloatArray(n + 1); val ny = FloatArray(n + 1)
        for (i in 0..n) {
            val u = i / n.toFloat()
            mx[i] = len * u; my[i] = -w * .9f * sin(PI.toFloat() * u * .9f) + len * .22f * u * u
        }
        for (i in 0..n) {
            val a = (i - 1).coerceAtLeast(0); val c = (i + 1).coerceAtMost(n)
            val dx = mx[c] - mx[a]; val dy = my[c] - my[a]; val l = hypot(dx, dy)
            nx[i] = dy / l; ny[i] = -dx / l
        }
        // sin(π) en Float vaut −8.7e-8 : sans le plancher à 0, sa puissance donne NaN et tout le limbe disparaît.
        fun width(u: Float) = w * sin(PI.toFloat() * u).coerceAtLeast(0f).pow(.55f) * (if (u < .08f) u / .08f else 1f)
        val blade = Path()
        blade.moveTo(mx[0], my[0])
        for (i in 0..n) { val u = i / n.toFloat(); blade.lineTo(mx[i] + nx[i] * width(u), my[i] + ny[i] * width(u)) }
        for (i in n downTo 0) { val u = i / n.toFloat(); blade.lineTo(mx[i] - nx[i] * width(u) * .85f, my[i] - ny[i] * width(u) * .85f) }
        blade.close()
        b.wash(blade, 0xFF5E9A44.toInt(), 255)
        b.washGradient(blade, 0xFFB8D86A.toInt(), 0f, -w, 160, 0f, w, 0)
        // Les nervures secondaires, obliques vers la pointe.
        val vein = b.pen(withAlpha(0xFF2E5A22.toInt(), 200), .45f)
        for (i in 1 until n) {
            val u = i / n.toFloat()
            for (side in floatArrayOf(1f, -.85f)) {
                val ex = mx[i] + nx[i] * width(u) * side + (if (side > 0) 3f else 3f)
                val ey = my[i] + ny[i] * width(u) * side
                b.c.drawLine(mx[i], my[i], ex, ey, vein)
            }
        }
        b.hatch(blade, 30f, 1.8f, .4f, Ink.SEPIA, Fade(0f, w, 0f, -w * .4f, 200, 0))
        b.stroke(blade, .8f, withAlpha(0xFF2A3A18.toInt(), 255))
        // Les déchirures : des fentes jusqu'à la nervure, découpées dans la feuille.
        val cut = b.pen(0xFF000000.toInt(), 1.2f)
        cut.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val rnd = Random(seed)
        repeat(6) {
            val i = 8 + rnd.nextInt(n - 12)
            val u = i / n.toFloat()
            val side = if (rnd.nextBoolean()) 1f else -.85f
            val depth = .4f + rnd.nextFloat() * .55f
            b.c.drawLine(mx[i] + nx[i] * width(u) * side * (1.02f), my[i] + ny[i] * width(u) * side * 1.02f,
                mx[i] + nx[i] * width(u) * side * (1f - depth) + 2f, my[i] + ny[i] * width(u) * side * (1f - depth), cut)
        }
        cut.xfermode = null
        // La nervure centrale, épaisse et claire.
        val rib = Path().apply { moveTo(mx[0], my[0]); for (i in 1..n) lineTo(mx[i], my[i]) }
        b.stroke(rib, 2.4f, withAlpha(0xFF2A3A18.toInt(), 255))
        b.stroke(rib, 1.4f, 0xFFD8E0A0.toInt())
    }

    /** Le régime : la hampe arquée, les mains de bananes tournées vers le haut, le bourgeon violet. */
    private fun bunch(b: Burin) {
        val stalk = Path().apply { moveTo(164f, 172f); cubicTo(190f, 166f, 214f, 176f, 222f, 196f); cubicTo(226f, 220f, 228f, 260f, 232f, 292f) }
        b.stroke(stalk, 5f, Ink.SEPIA)
        b.stroke(stalk, 3.4f, 0xFF7A8A3A.toInt())
        for (h in 0 until 5) {
            val cy = 206f + h * 17f
            val cx = 224f + h * 1.6f
            val fingers = 6 - h / 2
            for (f in 0 until fingers) {
                val off = (f - (fingers - 1) / 2f) * 7f
                val x0 = cx + off; val y0 = cy + 4f
                val banana = Path().apply {
                    moveTo(x0 - 2.4f, y0)
                    cubicTo(x0 - 4f + off * .4f, y0 - 8f, x0 - 2f + off * .7f, y0 - 16f, x0 + 1f + off * .9f, y0 - 20f)
                    cubicTo(x0 + 3f + off * .9f, y0 - 19f, x0 + 3f + off * .7f, y0 - 10f, x0 + 2.4f, y0)
                    close()
                }
                val ripe = h * .12f + f * .02f
                b.wash(banana, if (ripe > .35f) 0xFFC8C848.toInt() else 0xFF8AB040.toInt(), 255)
                b.hatch(banana, 80f, 1.2f, .4f, Ink.SEPIA, Fade(x0 + 3f, 0f, x0 - 1f, 0f, 220, 0))
                b.stroke(banana, .6f)
                b.c.drawCircle(x0 + 1f + off * .9f, y0 - 20f, .9f, b.pen(Ink.SEPIA))
            }
        }
        // Le bourgeon violet au bout de la hampe.
        val heart = Path().apply { moveTo(232f, 290f); cubicTo(242f, 294f, 244f, 308f, 234f, 318f); cubicTo(224f, 308f, 224f, 294f, 232f, 290f); close() }
        b.body(heart, 0xFF7A2A4A.toInt(), .45f, 70f, Fade(240f, 300f, 228f, 300f, 230, 0), outline = .8f, washAlpha = 255)
        b.stroke(Path().apply { moveTo(229f, 296f); cubicTo(232f, 302f, 236f, 304f, 240f, 304f) }, .6f, withAlpha(0xFFE8A8C8.toInt(), 220))
    }

    // ───────────── animation ─────────────

    private val wing = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val gust = .5f + .5f * sin(t * .45f)
        // Les feuilles de derrière d'abord, puis le régime, puis celles de devant.
        for ((i, l) in LEAVES.withIndex()) {
            if (l[4] > 0f) continue
            leafAt(c, s, l, i, t, gust)
        }
        s.draw(c, BUNCH, deg = 1.6f * sin(t * .9f) + 1.2f * gust, px = 164f, py = 172f)
        for ((i, l) in LEAVES.withIndex()) {
            if (l[4] <= 0f) continue
            leafAt(c, s, l, i, t, gust)
        }
        // Un papillon bleu.
        val p = t * .35f
        val bx = 100f + 70f * sin(p) + 20f * sin(p * 2.3f)
        val by = 140f + 34f * sin(p * 1.7f) + 10f * cos(p * 3.1f)
        val flap = abs(sin(t * 16f))
        c.save(); c.translate(bx, by); c.rotate(12f * sin(p * 2f))
        for (side in floatArrayOf(-1f, 1f)) {
            wing.color = 0xFF2A7AD0.toInt()
            c.drawOval(if (side < 0) -7f * flap else 0f, -5f, if (side < 0) 0f else 7f * flap, 1f, wing)
            wing.color = 0xFF1A2A4A.toInt()
            c.drawOval(if (side < 0) -5f * flap else 0f, 0f, if (side < 0) 0f else 5f * flap, 4f, wing)
        }
        ink.color = Ink.SEPIA; ink.strokeWidth = 1f
        c.drawLine(0f, -3f, 0f, 3f, ink)
        c.restore()
    }

    private fun leafAt(c: Canvas, s: Sprites, l: FloatArray, i: Int, t: Float, gust: Float) {
        val sway = 3.5f * sin(t * (1.1f + i * .13f) + i * 1.7f) + 4f * gust * sin(t * 2.3f + i)
        c.save(); c.translate(l[0], l[1]); c.rotate(l[2] + sway)
        if (l[3] < 0f) c.scale(-1f, 1f)
        s.draw(c, l[5].toInt())
        c.restore()
    }

    private companion object {
        const val LEAF_LONG = 1
        const val LEAF_MID = 2
        const val LEAF_YOUNG = 3
        const val BUNCH = 4
        /** Les feuilles : base x, y, angle, sens (−1 vers la gauche), devant (1) ou derrière, modèle. */
        val LEAVES = arrayOf(
            floatArrayOf(160f, 168f, 50f, -1f, 0f, 2f),
            floatArrayOf(164f, 166f, -38f, 1f, 0f, 2f),
            floatArrayOf(162f, 164f, -76f, 1f, 0f, 3f),
            floatArrayOf(158f, 170f, 18f, -1f, 1f, 1f),
            floatArrayOf(162f, 168f, -12f, 1f, 1f, 1f),
            floatArrayOf(160f, 168f, -16f, -1f, 1f, 2f)
        )
    }
}
