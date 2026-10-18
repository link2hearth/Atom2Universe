package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin
import kotlin.random.Random

/**
 * Uranium — la centrale nucléaire. Dans le réacteur, la fission des noyaux d'uranium chauffe
 * l'eau ; la vapeur fait tourner les turbines, et ce qui sort des grandes tours n'est que de la
 * vapeur d'eau. Les panaches montent et dérivent, les feux des tours clignotent, le fleuve coule.
 */
internal class SceneUranium : EngravedScene() {

    override val nameRes = R.string.card_scene_power_plant
    override val noteRes = R.string.card_note_power_plant
    override val explainRes = R.string.card_explain_power_plant

    override fun engrave(b: Burin) {
        hills(b)
        plant(b)
        for (tw in TOWERS) tower(b, tw[0], tw[1], tw[2], tw[3])
        pylons(b)
        river(b)
    }

    private fun hills(b: Burin) {
        val far = Path().apply {
            moveTo(40f, 214f); cubicTo(90f, 196f, 140f, 206f, 190f, 204f)
            cubicTo(240f, 202f, 280f, 190f, 320f, 200f); lineTo(320f, 262f); lineTo(40f, 262f); close()
        }
        b.body(far, 0xFF94A8A8.toInt(), .22f, 0f, Fade(0f, 196f, 0f, 250f, 40, 180), outline = .6f, washAlpha = 120)
        val field = rect(40f, 252f, 320f, 280f)
        b.body(field, 0xFF8AA06A.toInt(), .3f, 0f, outline = 0f, washAlpha = 170)
        val rnd = Random(3)
        var x = 44f
        while (x < 318f) {
            val r = 2.5f + rnd.nextFloat() * 2.5f
            b.body(ellipse(x, 254f - r * .4f, r, r * .8f), 0xFF4E6E3C.toInt(), .5f, 60f, outline = .45f, washAlpha = 200)
            x += r * 2f + rnd.nextFloat() * 10f
        }
    }

    /** Le bâtiment réacteur et son dôme, la salle des machines, la cheminée rayée. */
    private fun plant(b: Burin) {
        b.body(rect(214f, 238f, 300f, 268f), 0xFFD8D2C4.toInt(), .3f, 90f, Fade(300f, 0f, 220f, 0f, 200, 20), outline = .7f, washAlpha = 255)
        for (k in 0 until 6) b.c.drawRect(220f + k * 13f, 246f, 226f + k * 13f, 250f, b.pen(withAlpha(0xFF3A4A5A.toInt(), 200)))
        val dome = Path().apply {
            addRect(250f, 214f, 290f, 268f, Path.Direction.CW)
            op(Path().apply { addArc(250f, 196f, 290f, 232f, 180f, 180f); close() }, Path.Op.UNION)
        }
        b.body(dome, 0xFFE8E4DA.toInt(), .3f, 90f, Fade(290f, 0f, 258f, 0f, 230, 0), outline = .8f, washAlpha = 255)
        b.line(250f, 214f, 290f, 214f, .5f)
        val stack = rect(298f, 150f, 304f, 268f)
        b.body(stack, 0xFFF0EEE8.toInt(), .2f, 90f, outline = .6f, washAlpha = 255)
        for (k in 0 until 4) b.c.drawRect(298f, 150f + k * 22f, 304f, 160f + k * 22f, b.pen(0xFFC0392B.toInt()))
        b.stroke(stack, .6f)
    }

    /** Une tour de refroidissement : une hyperbole de béton sur sa couronne de piliers. */
    private fun tower(b: Burin, cx: Float, base: Float, top: Float, half: Float) {
        val h = base - top
        val waist = top + h * .62f
        val shape = Path().apply {
            moveTo(cx - half, base)
            cubicTo(cx - half * .78f, base - h * .2f, cx - half * .6f, waist + h * .08f, cx - half * .62f, waist)
            cubicTo(cx - half * .64f, waist - h * .2f, cx - half * .7f, top + h * .1f, cx - half * .72f, top)
            lineTo(cx + half * .72f, top)
            cubicTo(cx + half * .7f, top + h * .1f, cx + half * .64f, waist - h * .2f, cx + half * .62f, waist)
            cubicTo(cx + half * .6f, waist + h * .08f, cx + half * .78f, base - h * .2f, cx + half, base)
            close()
        }
        b.wash(shape, 0xFFE8E2D4.toInt(), 255)
        b.hatch(shape, 90f, 1.6f, .45f, Ink.SEPIA, Fade(cx + half, 0f, cx, 0f, 230, 0))
        b.hatch(shape, 90f, 2.4f, .4f, Ink.SEPIA, Fade(cx - half, 0f, cx - half * .4f, 0f, 160, 0))
        b.c.save(); b.c.clipPath(shape)
        var y = top + 8f
        while (y < base - 8f) { b.line(cx - half, y, cx + half, y, .35f, withAlpha(Ink.BROWN, 150)); y += 9f }
        b.c.restore()
        b.stroke(shape, .9f)
        b.body(ellipse(cx, top, half * .72f, 3.2f), 0xFF8A8A80.toInt(), .5f, 0f, outline = .7f, washAlpha = 255)
        // Les piliers obliques au pied.
        val legs = rect(cx - half, base - 7f, cx + half, base)
        b.wash(legs, 0xFF3A3A40.toInt(), 255)
        var lx = cx - half + 3f
        while (lx < cx + half - 2f) {
            b.line(lx, base, lx + 4f, base - 7f, 1.2f, 0xFFE8E2D4.toInt())
            b.line(lx + 4f, base, lx, base - 7f, 1.2f, 0xFFE8E2D4.toInt())
            lx += 8f
        }
    }

    private fun pylons(b: Burin) {
        for ((x, s) in listOf(70f to 1f, 316f to .7f)) {
            val base = 276f; val h = 62f * s; val w = 12f * s
            val ink = withAlpha(Ink.SEPIA, 230)
            b.line(x - w, base, x - w * .25f, base - h, .9f, ink); b.line(x + w, base, x + w * .25f, base - h, .9f, ink)
            var y = base
            var k = 0
            while (y > base - h + 6f) {
                val f = (base - y) / h
                val hw = w * (1f - f * .75f)
                val y2 = y - 8f * s
                val hw2 = w * (1f - (base - y2) / h * .75f)
                b.line(x - hw, y, x + hw2, y2, .5f, ink); b.line(x + hw, y, x - hw2, y2, .5f, ink)
                y = y2; k++
            }
            for (arm in floatArrayOf(base - h * .78f, base - h * .95f)) b.line(x - w * 1.4f, arm, x + w * 1.4f, arm, .9f, ink)
        }
        // Les câbles, en chaînette, d'un pylône à l'autre.
        for (k in 0 until 3) {
            val side = if (k == 0) -1f else 1f
            val y0 = 276f - 62f * (if (k == 2) .95f else .78f)
            val y1 = 276f - 62f * .7f * (if (k == 2) .95f else .78f)
            val x0 = 70f + side * 16f; val x1 = 316f + side * 11f
            val cable = Path().apply { moveTo(x0, y0); quadTo((x0 + x1) / 2f, (y0 + y1) / 2f + 22f, x1, y1) }
            b.stroke(cable, .5f, withAlpha(Ink.SEPIA, 200))
        }
    }

    private fun river(b: Burin) {
        val water = Path().apply {
            moveTo(40f, 282f); cubicTo(120f, 278f, 220f, 286f, 320f, 280f); lineTo(320f, 340f); lineTo(40f, 340f); close()
        }
        b.washGradient(water, 0xFF7EA6C4.toInt(), 0f, 280f, 255, 0f, 330f, 200)
        b.hatch(water, 0f, 1.8f, .45f, withAlpha(0xFF2E4A66.toInt(), 170), wobble = .5f)
        // Les reflets des tours, renversés et brisés.
        for (tw in TOWERS) {
            var y = 286f
            while (y < 316f) {
                val w = tw[3] * (.7f - (y - 286f) / 80f)
                b.line(tw[0] - w, y, tw[0] + w, y, 1f, withAlpha(0xFFF0EEE6.toInt(), 150))
                y += 3.2f
            }
        }
        val bank = Path().apply {
            moveTo(40f, 280f); cubicTo(120f, 274f, 220f, 282f, 320f, 276f); lineTo(320f, 283f); cubicTo(220f, 288f, 120f, 280f, 40f, 286f); close()
        }
        b.body(bank, 0xFF7A8A5A.toInt(), .4f, 0f, outline = .6f, washAlpha = 255)
        // Des roseaux sur la rive de devant.
        val rnd = Random(9)
        repeat(26) {
            val x = 44f + rnd.nextFloat() * 90f
            val h = 10f + rnd.nextFloat() * 14f
            b.taper(x, 330f, x + 1f, 330f - h * .5f, x + 2.5f, 330f - h * .8f, x + 4f * (rnd.nextFloat() - .3f), 330f - h, 1.4f, 0xFF4A5A2A.toInt())
        }
    }

    override fun sprites(): List<Sprite> = listOf(Sprite(PUFF, RectF(-30f, -24f, 30f, 24f)) { b ->
        val rnd = Random(4)
        val puff = Path()
        repeat(6) {
            val px = (rnd.nextFloat() - .5f) * 30f; val py = (rnd.nextFloat() - .5f) * 16f
            puff.op(ellipse(px, py, 11f + rnd.nextFloat() * 6f, 9f + rnd.nextFloat() * 4f), Path.Op.UNION)
        }
        b.fill(puff, 0xFFFBF8F0.toInt())
        b.hatch(puff, 0f, 2f, .4f, Ink.BROWN, Fade(0f, 20f, 0f, -6f, 200, 0))
        b.stroke(puff, .6f, withAlpha(Ink.BROWN, 200))
    })

    // ───────────── animation ─────────────

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les panaches de vapeur : des bouffées qui montent, gonflent, dérivent et s'effacent.
        for ((k, tw) in TOWERS.withIndex()) {
            for (i in PUFFS - 1 downTo 0) {
                val p = ((t / LIFE + i / PUFFS.toFloat() + k * .37f) % 1f)
                val x = tw[0] + p * 52f + 4f * sin(t * .6f + i)
                val y = tw[2] - 4f - p * 118f
                val sc = (.55f + p * 1.5f) * tw[3] / 40f
                val a = when { p < .08f -> p / .08f; else -> (1f - (p - .08f) / .92f) }
                c.save(); c.translate(x, y); c.scale(sc, sc)
                s.draw(c, PUFF, alpha = (240 * a).toInt())
                c.restore()
            }
        }
        // Les feux rouges en haut des tours et de la cheminée.
        val blink = (t % 1.6f) < .35f
        if (blink) {
            fill.color = 0xFFFF3A2A.toInt()
            for (tw in TOWERS) { c.drawCircle(tw[0] - tw[3] * .7f, tw[2] + 1f, 1.6f, fill); c.drawCircle(tw[0] + tw[3] * .7f, tw[2] + 1f, 1.6f, fill) }
            c.drawCircle(301f, 149f, 1.6f, fill)
            fill.color = withAlpha(0xFFFF3A2A.toInt(), 70)
            c.drawCircle(301f, 149f, 4.5f, fill)
        }
        // Le fleuve coule : des rides claires qui filent vers la droite.
        ink.strokeWidth = .8f
        for (i in 0 until 14) {
            val y = 290f + (i * 37 % 26).toFloat()
            val x = (t * (14f + (i % 4) * 4f) + i * 61f) % 300f + 30f
            val w = 4f + (i % 3) * 3f
            ink.color = withAlpha(0xFFFFFFFF.toInt(), 150)
            c.drawLine(x, y, x + w, y, ink)
        }
        // Deux oiseaux.
        ink.color = Ink.SEPIA; ink.strokeWidth = .8f
        for (i in 0..1) {
            val x = 330f - ((t * 11f + i * 150f) % 300f)
            val y = 100f + i * 18f + 5f * sin(t * .8f + i)
            val w = 3f * sin(t * 8f + i * 2f)
            c.drawLine(x - 5f, y - w, x, y, ink); c.drawLine(x, y, x + 5f, y - w, ink)
        }
    }

    private companion object {
        const val PUFF = 1
        const val PUFFS = 9
        const val LIFE = 8f
        /** Les tours : centre, pied, sommet, demi-largeur du pied. */
        val TOWERS = arrayOf(floatArrayOf(112f, 272f, 158f, 40f), floatArrayOf(190f, 270f, 170f, 34f))
    }
}
