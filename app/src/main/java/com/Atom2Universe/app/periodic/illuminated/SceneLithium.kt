package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lithium — la batterie, en planche de cabinet de physique. Une pile cylindrique ouverte sur
 * son rouleau de feuilles, et une loupe sur ce qui se passe dedans : à la charge, les ions
 * lithium quittent la cathode, traversent le séparateur et se glissent entre les feuillets de
 * graphite ; à la décharge, ils repartent. La jauge suit.
 */
internal class SceneLithium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_li_ion_cell
    override val noteRes = R.string.card_note_li_ion_cell
    override val explainRes = R.string.card_explain_li_ion_cell

    override fun engrave(b: Burin) {
        b.glow(190f, 180f, 180f, 0xFFB8D4E8.toInt(), 110)
        bench(b)
        cell(b)
        loupe(b)
    }

    private fun bench(b: Burin) {
        val top = rect(40f, BENCH, 320f, 340f)
        b.body(top, 0xFFA87A50.toInt(), .3f, 0f, Fade(0f, BENCH, 0f, 320f, 90, 255), outline = 0f, washAlpha = 210)
        for (i in 0 until 8) {
            val y = BENCH + 3f + i * 3.6f
            val grain = Path().apply {
                moveTo(40f, y); cubicTo(110f, y - 2f + i % 3, 200f, y + 2.5f, 320f, y - 1f)
            }
            b.stroke(grain, .5f, withAlpha(Ink.BROWN, 170))
        }
        b.line(40f, BENCH, 320f, BENCH, 1f)
        b.c.drawOval(CX - RX - 8f, BOT - 4f, CX + RX + 34f, BOT + 9f, b.pen(withAlpha(Ink.SEPIA, 70)))
    }

    private fun cell(b: Burin) {
        val l = CX - RX; val r = CX + RX
        val body = Path().apply {
            addRect(l, TOP, r, BOT, Path.Direction.CW)
            op(ellipse(CX, BOT, RX, RY), Path.Op.UNION)
        }
        // L'enveloppe : la gaine bleue sur le godet d'acier.
        b.wash(body, STEEL, 255)
        val wrap = Path().apply {
            addRect(l, TOP + 10f, r, BOT - 6f, Path.Direction.CW)
            op(ellipse(CX, BOT - 6f, RX, RY), Path.Op.UNION)
            op(ellipse(CX, TOP + 10f, RX, RY), Path.Op.DIFFERENCE)
        }
        b.wash(wrap, WRAP, 255)
        b.hatch(body, 90f, 1.5f, .5f, Ink.SEPIA, Fade(r, 0f, CX + 6f, 0f, 240, 0))
        b.hatch(body, 90f, 2f, .45f, Ink.SEPIA, Fade(l, 0f, l + 16f, 0f, 200, 0))
        b.c.drawRect(l + 12f, TOP + 10f, l + 19f, BOT - 2f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 80)))
        b.stroke(body, 1.1f)
        b.stroke(Path().apply { addArc(l, TOP + 10f - RY, r, TOP + 10f + RY, 0f, 180f) }, .8f)
        b.stroke(Path().apply { addArc(l, BOT - 6f - RY, r, BOT - 6f + RY, 0f, 180f) }, .8f)
        // Le couvercle, son sertissage et la borne positive.
        val lid = ellipse(CX, TOP, RX, RY)
        b.body(lid, STEEL, .25f, 0f, Fade(CX - RX, 0f, CX + RX, 0f, 0, 200), outline = 1f, washAlpha = 255)
        b.stroke(ellipse(CX, TOP + 1f, RX - 6f, RY - 1.8f), .7f)
        val post = Path().apply {
            addRect(CX - 12f, TOP - 7f, CX + 12f, TOP, Path.Direction.CW)
            op(ellipse(CX, TOP, 12f, 2.6f), Path.Op.UNION)
        }
        b.body(post, STEEL, .35f, 90f, Fade(CX + 12f, 0f, CX - 4f, 0f, 230, 0), outline = .8f, washAlpha = 255)
        b.body(ellipse(CX, TOP - 7f, 12f, 2.6f), 0xFFDCE0E6.toInt(), 0f, outline = .8f, washAlpha = 255)
        // Le symbole « + » et l'éclair gravés sur la gaine.
        b.line(l + 14f, TOP + 24f, l + 22f, TOP + 24f, 1.4f, Gilding.LEAF)
        b.line(l + 18f, TOP + 20f, l + 18f, TOP + 28f, 1.4f, Gilding.LEAF)
        b.gold(bolt)
        b.stroke(bolt, .6f)
        // La jauge : cinq cases éteintes.
        for (i in 0 until 5) {
            val y = GAUGE_BOT - i * 13f
            b.body(rect(GAUGE_L, y - 10f, GAUGE_L + 14f, y), 0xFF1E2A36.toInt(), 0f, outline = .7f, washAlpha = 230)
        }
        cutaway(b)
    }

    /** L'enveloppe ouverte sur le quart avant droit : le rouleau de feuilles, vu de côté et d'en haut. */
    private fun cutaway(b: Burin) {
        val r = CX + RX
        val cut = Path().apply {
            moveTo(CX + 4f, CUT_T + edge(CX + 4f))
            var x = CX + 4f
            while (x < r - 2f) { x += 2f; lineTo(x, CUT_T + edge(x)) }
            lineTo(r - 2f, CUT_B + edge(r - 2f))
            x = r - 2f
            while (x > CX + 4f) { x -= 2f; lineTo(x, CUT_B + edge(x)) }
            close()
        }
        b.fill(cut, 0xFF1B1E26.toInt())
        b.c.save(); b.c.clipPath(cut)
        val layers = intArrayOf(COPPER, GRAPHITE, SEPARATOR, OXIDE, ALU, OXIDE, SEPARATOR, GRAPHITE)
        var x = CX + 4f; var k = 0
        while (x < r) {
            val w = 1.6f * (1f - ((x - CX) / RX) * .45f)
            b.c.drawRect(x, CUT_T - 10f, x + w, CUT_B + 12f, b.pen(layers[k % layers.size]))
            x += w; k++
        }
        b.hatch(cut, 90f, 1.4f, .45f, Ink.SEPIA, Fade(r, 0f, CX + 12f, 0f, 220, 0))
        b.c.restore()
        // Le dessus du rouleau : une spirale vue de biais.
        val top = Path().apply {
            moveTo(CX + 4f, CUT_T)
            var x = CX + 4f
            while (x < r - 2f) { x += 2f; lineTo(x, CUT_T + edge(x)) }
            lineTo(CX + 4f, CUT_T); close()
        }
        b.wash(top, 0xFF8A7A6A.toInt(), 255)
        for (i in 1..9) {
            val rr = 4f + i * 3.8f
            b.c.drawArc(CX - rr, CUT_T - rr * RY / RX, CX + rr, CUT_T + rr * RY / RX, 0f, 90f, false,
                b.pen(if (i % 2 == 0) COPPER else Ink.SEPIA, .6f))
        }
        b.stroke(cut, 1.4f, STEEL)
        b.stroke(cut, .6f)
    }

    /** Le bord du cylindre vu de face : la demi-ellipse sous la ligne d'axe. */
    private fun edge(x: Float): Float {
        val u = ((x - CX) / RX).coerceIn(-1f, 1f)
        return RY * sqrt(1f - u * u)
    }

    private fun loupe(b: Burin) {
        // Les deux traits qui relient la loupe au rouleau.
        val d = hypot(LX - FX, LY - FY)
        val a = atan2(LY - FY, LX - FX)
        val s = asin(LR / d)
        val len = sqrt(d * d - LR * LR)
        for (sign in floatArrayOf(-1f, 1f)) {
            val ang = a + sign * s
            b.line(FX, FY, FX + cos(ang) * len, FY + sin(ang) * len, .7f, withAlpha(Ink.SEPIA, 200))
        }
        b.c.drawCircle(FX, FY, 3f, b.pen(Ink.SEPIA, .8f))
        val disc = ellipse(LX, LY, LR, LR)
        b.fill(disc, 0xFFF3EBD0.toInt())
        b.c.save(); b.c.clipPath(disc)
        // Le collecteur de cuivre et les feuillets de graphite, à gauche.
        b.c.drawRect(LX - LR, LY - LR, LX - 44f, LY + LR, b.pen(COPPER))
        b.hatch(rect(LX - LR, LY - LR, LX - 44f, LY + LR), 90f, 1.6f, .45f, Ink.SEPIA)
        for (k in -6..6) {
            val y = LY + k * 9f
            b.c.drawRect(LX - 44f, y - 1.3f, LX - 18f, y + 1.3f, b.pen(GRAPHITE))
            var x = LX - 44f
            val zig = b.pen(withAlpha(0xFF9AA0AA.toInt(), 200), .4f)
            while (x < LX - 18f) { b.c.drawLine(x, y - 1.3f, x + 2f, y + 1.3f, zig); b.c.drawLine(x + 2f, y + 1.3f, x + 4f, y - 1.3f, zig); x += 4f }
        }
        // Le séparateur poreux, au milieu.
        b.c.drawRect(LX - 3f, LY - LR, LX + 3f, LY + LR, b.pen(withAlpha(0xFFFFFFFF.toInt(), 220)))
        var y = LY - LR
        while (y < LY + LR) { b.line(LX, y, LX, y + 3f, .6f, withAlpha(Ink.SEPIA, 170)); y += 5.5f }
        // L'oxyde de la cathode, en lames, et le collecteur d'aluminium, à droite.
        for (k in -6..6) {
            val ly = LY + k * 9f + 4.5f
            b.c.drawRect(LX + 18f, ly - 2f, LX + 44f, ly + 2f, b.pen(OXIDE))
            var x = LX + 20f
            while (x < LX + 44f) { b.c.drawCircle(x, ly - 2f, .9f, b.pen(0xFFB8452E.toInt())); b.c.drawCircle(x + 2.5f, ly + 2f, .9f, b.pen(0xFFB8452E.toInt())); x += 5f }
        }
        b.c.drawRect(LX + 44f, LY - LR, LX + LR, LY + LR, b.pen(ALU))
        b.hatch(rect(LX + 44f, LY - LR, LX + LR, LY + LR), 90f, 1.8f, .45f, Ink.SEPIA)
        b.stipple(rect(LX - 18f, LY - LR, LX + 18f, LY + LR), 90, .5f, withAlpha(0xFFA89060.toInt(), 160), 4)
        b.c.restore()
        // La monture : laiton, filets d'or, ombre intérieure.
        b.c.drawCircle(LX, LY, LR - 1f, b.pen(withAlpha(Ink.SEPIA, 60), 3f))
        b.c.drawCircle(LX, LY, LR + 3f, b.pen(0xFFB08A3A.toInt(), 6f))
        b.goldStroke(Path().apply { addCircle(LX, LY, LR + 3f, Path.Direction.CW) }, 2f)
        b.c.drawCircle(LX, LY, LR, b.pen(Ink.SEPIA, .9f))
        b.c.drawCircle(LX, LY, LR + 6.2f, b.pen(Ink.SEPIA, .9f))
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(ION, RectF(-5f, -5f, 5f, 5f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(-1.2f, -1.4f, 5f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6B8D0.toInt(), 0xFF9A5A86.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 3.6f, b.p)
            b.c.drawCircle(0f, 0f, 3.6f, b.pen(Ink.SEPIA, .5f))
            b.line(-1.5f, 0f, 1.5f, 0f, .6f); b.line(0f, -1.5f, 0f, 1.5f, .6f)
        },
        Sprite(GLOW, RectF(-30f, -30f, 30f, 30f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 30f,
                intArrayOf(0xCCFFF2B0.toInt(), 0x44FFE08A, 0x00FFE08A), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 30f, b.p)
        }
    )

    // ───────────── animation ─────────────

    private val lamp = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val u = t % CYCLE
        val charging = u < 5f
        val level = when {
            u < 5f -> u / 5f
            u < 6f -> 1f
            else -> 1f - (u - 6f) / 3f
        }
        // La jauge.
        for (i in 0 until 5) {
            val y = GAUGE_BOT - i * 13f
            val on = level >= (i + 1) / 5f - .001f
            val next = !on && level > i / 5f
            val alpha = when {
                on -> 255
                next && charging -> (140 + 110 * sin(t * 9f)).toInt()
                else -> 0
            }
            if (alpha <= 0) continue
            lamp.color = withAlpha(if (level < .25f && !charging) 0xFFE0702E.toInt() else 0xFF7CD35A.toInt(), alpha)
            c.drawRect(GAUGE_L + 1.6f, y - 8.4f, GAUGE_L + 12.4f, y - 1.6f, lamp)
        }
        // L'éclair s'illumine pendant la charge.
        if (charging) {
            c.save(); c.translate(BOLT_X, BOLT_Y)
            s.draw(c, GLOW, alpha = (150 + 90 * sin(t * 6f)).toInt())
            c.restore()
        }
        // Les ions : de la cathode au graphite pendant la charge, retour à la décharge.
        c.save(); c.clipPath(lens)
        for (i in 0 until IONS) {
            val p = (level * (IONS + 1.5f) - i).coerceIn(0f, 1f)
            val e = p * p * (3f - 2f * p)
            val k = i % 7 - 3
            val cy = LY + k * 9f; val ay = LY + k * 9f + 4.5f
            val cx = LX + 24f + (i % 3) * 7f
            val ax = LX - 22f - ((i + 1) % 3) * 7f
            val wave = sin(PI.toFloat() * e) * 7f * (if (i % 2 == 0) 1f else -1f)
            val x = cx + (ax - cx) * e + .5f * sin(t * 9f + i * 2f)
            val y = cy + (ay - cy) * e + wave + .5f * cos(t * 8f + i)
            s.draw(c, ION, dx = x, dy = y)
        }
        c.restore()
    }

    private val lens = ellipse(LX, LY, LR, LR)
    private val bolt = poly(
        BOLT_X + 3f, BOLT_Y - 12f, BOLT_X - 5f, BOLT_Y + 1f, BOLT_X - .5f, BOLT_Y + 1f,
        BOLT_X - 3f, BOLT_Y + 12f, BOLT_X + 5f, BOLT_Y - 1f, BOLT_X + .5f, BOLT_Y - 1f
    )

    private companion object {
        const val CX = 140f
        const val RX = 42f
        const val RY = 9f
        const val TOP = 98f
        const val BOT = 282f
        const val BENCH = 286f
        const val CUT_T = 146f
        const val CUT_B = 252f
        const val LX = 252f
        const val LY = 172f
        const val LR = 54f
        const val FX = 168f
        const val FY = 200f
        const val GAUGE_L = 104f
        const val GAUGE_BOT = 262f
        const val BOLT_X = 118f
        const val BOLT_Y = 160f
        const val CYCLE = 9f
        const val IONS = 10
        const val ION = 1
        const val GLOW = 2
        const val STEEL = 0xFFB8BEC6.toInt()
        const val WRAP = 0xFF2E5C8A.toInt()
        const val COPPER = 0xFFC77B4A.toInt()
        const val GRAPHITE = 0xFF34343A.toInt()
        const val SEPARATOR = 0xFFF4F0E6.toInt()
        const val OXIDE = 0xFF56708C.toInt()
        const val ALU = 0xFFD0D4DA.toInt()
    }
}
