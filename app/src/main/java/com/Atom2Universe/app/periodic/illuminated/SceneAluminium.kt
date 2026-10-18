package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Aluminium — la canette. Léger, et il ne rouille pas : l'aluminium fait les canettes, et se
 * recycle sans fin. Une canette ouverte sur une table de terrasse au bord de la mer, à côté de
 * son verre plein de glaçons : les bulles montent, une goutte de buée glisse, une guêpe tourne
 * autour, la mer scintille et le volant du parasol ondule.
 */
internal class SceneAluminium : EngravedScene() {

    override val nameRes = R.string.card_scene_soda_can
    override val noteRes = R.string.card_note_soda_can
    override val explainRes = R.string.card_explain_soda_can

    override fun engrave(b: Burin) {
        sea(b)
        table(b)
        glass(b)
        can(b)
    }

    private fun sea(b: Burin) {
        // Un cap au loin, puis la mer jusqu'à la terrasse.
        val cape = Path().apply {
            moveTo(236f, HORIZON); cubicTo(256f, 170f, 284f, 164f, 320f, 168f); lineTo(320f, HORIZON); close()
        }
        b.body(cape, 0xFF8A9C88.toInt(), .3f, 0f, Fade(0f, 164f, 0f, HORIZON, 60, 180), outline = .6f, washAlpha = 170)
        val sea = rect(40f, HORIZON, 320f, 250f)
        b.wash(sea, 0xFF3E7EA6.toInt(), 220)
        b.washGradient(sea, 0xFF9CC8DC.toInt(), 0f, HORIZON, 200, 0f, 230f, 0)
        b.hatchRect(RectF(40f, HORIZON, 320f, 250f), 0f, 2f, .45f, 0xFF1E3E58.toInt(), Fade(0f, HORIZON, 0f, 240f, 220, 90))
        b.line(40f, HORIZON, 320f, HORIZON, .8f)
        // La rambarde blanche de la terrasse.
        val rail = rect(40f, 222f, 320f, 226f)
        b.body(rail, 0xFFF4F2EA.toInt(), 0f, outline = .7f, washAlpha = 255)
        var x = 48f
        while (x < 320f) { b.body(rect(x, 226f, x + 3f, 250f), 0xFFF4F2EA.toInt(), .25f, 90f, outline = .5f, washAlpha = 255); x += 14f }
    }

    /** Le plateau rond de la table, en marbre, vu en perspective. */
    private fun table(b: Burin) {
        val top = ellipse(180f, 278f, 160f, 34f)
        b.wash(top, 0xFFF2EEE6.toInt(), 255)
        b.washGradient(top, 0xFF8A8478.toInt(), 0f, 250f, 0, 0f, 312f, 120)
        val vein = b.pen(withAlpha(0xFF9A9488.toInt(), 150), .5f)
        val rnd = Random(12)
        repeat(9) {
            val x0 = 40f + rnd.nextFloat() * 280f; val y0 = 252f + rnd.nextFloat() * 50f
            val p = Path().apply {
                moveTo(x0, y0)
                cubicTo(x0 + 20f, y0 + rnd.nextFloat() * 8f - 4f, x0 + 30f, y0 + rnd.nextFloat() * 12f - 6f, x0 + 52f, y0 + rnd.nextFloat() * 10f - 5f)
            }
            b.c.save(); b.c.clipPath(top); b.c.drawPath(p, vein); b.c.restore()
        }
        b.stroke(top, 1f)
        // Les ombres de la canette et du verre, portées vers la droite.
        b.fill(ellipse(CX + 18f, CAN_BOTTOM + 3f, CAN_R + 8f, 5f), withAlpha(0xFF3A3228.toInt(), 70))
        b.fill(ellipse(GLASS_X + 14f, GLASS_BOTTOM + 3f, 22f, 4f), withAlpha(0xFF3A3228.toInt(), 50))
        // Une rondelle de citron sur la table.
        val lemon = ellipse(256f, 292f, 15f, 6f)
        b.body(lemon, 0xFFF2DA48.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        b.body(ellipse(256f, 291.6f, 12f, 4.6f), 0xFFFAF0A0.toInt(), 0f, outline = 0f, washAlpha = 255)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            b.line(256f, 291.6f, 256f + cos(a) * 11f, 291.6f + sin(a) * 4.2f, .5f, withAlpha(0xFFC8A830.toInt(), 220))
        }
    }

    /** Le verre de soda : glaçons, paille rayée. */
    private fun glass(b: Burin) {
        val glass = Path().apply {
            moveTo(GLASS_X - 20f, GLASS_TOP); lineTo(GLASS_X + 20f, GLASS_TOP)
            lineTo(GLASS_X + 16f, GLASS_BOTTOM); lineTo(GLASS_X - 16f, GLASS_BOTTOM); close()
        }
        val soda = Path().apply { op(glass, rect(0f, LIQUID_Y, 360f, 400f), Path.Op.INTERSECT) }
        b.wash(glass, 0xFFE8F2F4.toInt(), 90)
        b.wash(soda, 0xFFE8B850.toInt(), 200)
        b.washGradient(soda, 0xFF8A5A18.toInt(), GLASS_X + 16f, 0f, 160, GLASS_X - 10f, 0f, 0)
        // Les glaçons qui flottent.
        for ((x, y, a) in listOf(Triple(GLASS_X - 8f, LIQUID_Y + 4f, 12f), Triple(GLASS_X + 7f, LIQUID_Y + 2f, -18f), Triple(GLASS_X, LIQUID_Y + 14f, 30f))) {
            b.c.save(); b.c.rotate(a, x, y)
            val cube = Path().apply { addRoundRect(x - 6f, y - 6f, x + 6f, y + 6f, 2f, 2f, Path.Direction.CW) }
            b.wash(cube, 0xFFF4FAFC.toInt(), 170)
            b.stroke(cube, .6f, withAlpha(0xFF5A7A8A.toInt(), 220))
            b.line(x - 3.5f, y - 3.5f, x + 1f, y - 3.5f, .7f, 0xFFFFFFFF.toInt())
            b.c.restore()
        }
        // La paille, rayée de rouge.
        val straw = limb(GLASS_X + 4f, GLASS_BOTTOM - 8f, GLASS_X + 18f, GLASS_TOP - 30f, 3.6f, 3.6f)
        b.wash(straw, 0xFFFFFFFF.toInt(), 255)
        b.c.save(); b.c.clipPath(straw)
        var k = 0f
        while (k < 1f) {
            val x = GLASS_X + 4f + 14f * k; val y = GLASS_BOTTOM - 8f + (GLASS_TOP - 30f - GLASS_BOTTOM + 8f) * k
            b.line(x - 4f, y + 2f, x + 4f, y - 2f, 2.2f, 0xFFD03A30.toInt())
            k += .08f
        }
        b.c.restore()
        b.stroke(straw, .6f)
        b.stroke(glass, 1.2f, withAlpha(Ink.SEPIA, 220))
        b.line(GLASS_X - 15f, GLASS_TOP + 6f, GLASS_X - 12.5f, GLASS_BOTTOM - 6f, 1.6f, withAlpha(0xFFFFFFFF.toInt(), 200))
        b.body(ellipse(GLASS_X, GLASS_TOP, 20f, 3.4f), 0xFFE8F2F4.toInt(), 0f, outline = .9f, washAlpha = 120)
    }

    /** La canette : le métal nu en haut et en bas, l'étiquette verte au citron, et la buée. */
    private fun can(b: Burin) {
        val left = CX - CAN_R; val right = CX + CAN_R
        val shape = Path().apply {
            moveTo(left + 4f, CAN_TOP + 8f)
            cubicTo(left + 1f, CAN_TOP + 12f, left, CAN_TOP + 16f, left, CAN_TOP + 20f)
            lineTo(left, CAN_BOTTOM - 6f)
            cubicTo(left, CAN_BOTTOM - 1f, left + 6f, CAN_BOTTOM + 2f, CX, CAN_BOTTOM + 2f)
            cubicTo(right - 6f, CAN_BOTTOM + 2f, right, CAN_BOTTOM - 1f, right, CAN_BOTTOM - 6f)
            lineTo(right, CAN_TOP + 20f)
            cubicTo(right, CAN_TOP + 16f, right - 1f, CAN_TOP + 12f, right - 4f, CAN_TOP + 8f)
            close()
        }
        // Le métal : un dégradé de cylindre, sombre aux bords, un reflet franc à gauche.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(left, 0f, right, 0f,
            intArrayOf(0xFF5A6066.toInt(), 0xFFF4F6F8.toInt(), 0xFFC4CACE.toInt(), 0xFF8A9096.toInt(), 0xFF3E4448.toInt()),
            floatArrayOf(0f, .22f, .4f, .75f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(shape, b.p)
        // L'étiquette : verte, une vague blanche, une rondelle de citron.
        b.c.save(); b.c.clipPath(shape)
        val label = rect(left, CAN_TOP + 24f, right, CAN_BOTTOM - 10f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(left, 0f, right, 0f,
            intArrayOf(0xFF1E5A3A.toInt(), 0xFF6ACA8A.toInt(), 0xFF3A9A62.toInt(), 0xFF1E6A42.toInt(), 0xFF0E2E1E.toInt()),
            floatArrayOf(0f, .22f, .4f, .75f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(label, b.p)
        val wave = Path().apply {
            moveTo(left, 214f); cubicTo(CX - 10f, 200f, CX + 6f, 232f, right, 216f)
            lineTo(right, 224f); cubicTo(CX + 6f, 240f, CX - 10f, 208f, left, 222f); close()
        }
        b.wash(wave, 0xFFF4F6F0.toInt(), 235)
        val lx = CX - 4f; val ly = 186f
        b.body(ellipse(lx, ly, 13f, 13f), 0xFFF2DA48.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        b.body(ellipse(lx, ly, 10f, 10f), 0xFFFAF0A0.toInt(), 0f, outline = 0f, washAlpha = 255)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            b.line(lx, ly, lx + cos(a) * 9.4f, ly + sin(a) * 9.4f, .7f, 0xFFC8A830.toInt())
        }
        b.hatch(label, 90f, 1.8f, .4f, Ink.SEPIA, Fade(right, 0f, CX + 6f, 0f, 220, 0))
        b.c.restore()
        b.line(left, CAN_TOP + 24f, right, CAN_TOP + 24f, .6f)
        b.line(left, CAN_BOTTOM - 10f, right, CAN_BOTTOM - 10f, .6f)
        // La buée : des perles d'eau, chacune avec son éclat.
        val rnd = Random(31)
        val drop = b.pen(withAlpha(0xFF0E2E1E.toInt(), 110))
        val shine = b.pen(withAlpha(0xFFFFFFFF.toInt(), 220))
        repeat(90) {
            val x = left + 3f + rnd.nextFloat() * (CAN_R * 2f - 6f); val y = CAN_TOP + 26f + rnd.nextFloat() * (CAN_BOTTOM - CAN_TOP - 40f)
            val r = .5f + rnd.nextFloat() * 1.1f
            b.c.drawCircle(x + .3f, y + .4f, r, drop)
            b.c.drawCircle(x - r * .3f, y - r * .3f, r * .45f, shine)
        }
        b.stroke(shape, 1.1f)
        // Le couvercle, son rebord, l'ouverture et l'anneau relevé.
        val lid = ellipse(CX, CAN_TOP + 6f, CAN_R - 4f, 6f)
        b.wash(lid, 0xFFB8BEC4.toInt(), 255)
        b.washGradient(lid, 0xFFFFFFFF.toInt(), CX - 20f, 0f, 200, CX + 10f, 0f, 0)
        b.stroke(lid, 1f)
        b.body(ellipse(CX, CAN_TOP + 7f, CAN_R - 8f, 4.2f), 0xFF9AA0A6.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        val hole = Path().apply { addRoundRect(CX - 12f, CAN_TOP + 6f, CX - 2f, CAN_TOP + 10f, 2f, 2f, Path.Direction.CW) }
        b.fill(hole, 0xFF14181A.toInt())
        val tab = Path().apply { addRoundRect(CX - 2f, CAN_TOP - 5f, CX + 7f, CAN_TOP + 8f, 3f, 3f, Path.Direction.CW) }
        b.c.save(); b.c.rotate(-14f, CX + 2f, CAN_TOP + 8f)
        b.body(tab, 0xFFD8DCE0.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(CX + .5f, CAN_TOP - 2f, CX + 4.5f, CAN_TOP + 2f, 1.5f, 1.5f, Path.Direction.CW) }, 0xFF5A6066.toInt(), 0f, outline = .4f, washAlpha = 255)
        b.c.restore()
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fringe = Path()
    private val glassClip = Path().apply {
        moveTo(GLASS_X - 19f, LIQUID_Y); lineTo(GLASS_X + 19f, LIQUID_Y)
        lineTo(GLASS_X + 15.5f, GLASS_BOTTOM - 1f); lineTo(GLASS_X - 15.5f, GLASS_BOTTOM - 1f); close()
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        sea(c, t)
        // Un voilier passe au large, très lentement.
        val bx = 330f - (t * 3f) % 300f
        fill.color = 0xFFF8F4EA.toInt()
        c.drawPath(sail(bx), fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = .6f
        c.drawPath(sail(bx), ink)
        c.drawLine(bx - 7f, HORIZON - 1.5f, bx + 7f, HORIZON - 1.5f, ink)
        // Les bulles du verre.
        c.save(); c.clipPath(glassClip)
        for (k in 0 until 12) {
            val life = (t / (1.4f + (k % 4) * .35f) + k * .37f) % 1f
            val x = GLASS_X - 12f + (k * 7.3f) % 24f + sin(life * 7f + k) * 1.2f
            val y = GLASS_BOTTOM - 3f - life * (GLASS_BOTTOM - LIQUID_Y - 2f)
            ink.color = withAlpha(0xFFFFF8E0.toInt(), 220); ink.strokeWidth = .5f
            c.drawCircle(x, y, .7f + life * .8f, ink)
        }
        c.restore()
        // Le pétillement à l'ouverture de la canette.
        for (k in 0 until 7) {
            val life = (t / .9f + k / 7f) % 1f
            val x = CX - 7f + 6f * hash(k, (t / .9f + k / 7f).toInt()) - 3f
            val y = CAN_TOP + 6f - life * 10f
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (220 * (1f - life)).toInt())
            c.drawCircle(x, y, .5f + .5f * (1f - life), fill)
        }
        // Une goutte de buée qui glisse, et la traînée humide qu'elle laisse.
        val cycle = t / DROP_PERIOD
        val n = cycle.toInt()
        val u = cycle - n
        val dx = CX - 18f + 34f * hash(n, 7)
        val start = CAN_TOP + 34f + 30f * hash(n, 8)
        val slide = (u * 1.6f).coerceIn(0f, 1f)
        val dy = start + (CAN_BOTTOM - 14f - start) * slide * slide
        val fade = if (u < .85f) 1f else (1f - u) / .15f
        ink.color = withAlpha(0xFF0E2E1E.toInt(), (90 * fade).toInt()); ink.strokeWidth = 1.2f
        c.drawLine(dx, start, dx + .4f, dy, ink)
        fill.color = withAlpha(0xFF0E2E1E.toInt(), (150 * fade).toInt())
        c.drawCircle(dx + .4f, dy + .6f, 1.9f, fill)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), (240 * fade).toInt())
        c.drawCircle(dx - .3f, dy - .4f, .9f, fill)
        wasp(c, t)
        parasol(c, t)
    }

    /** Les éclats du soleil sur la mer. */
    private fun sea(c: Canvas, t: Float) {
        val slot = (t / .7f).toInt()
        val life = t / .7f - slot
        val tw = sin(life * PI.toFloat())
        for (k in 0 until 6) {
            val x = 50f + 260f * hash(slot * 7 + k, 1)
            val y = HORIZON + 4f + 30f * hash(slot * 7 + k, 2)
            val r = (1.5f + 2.5f * (y - HORIZON) / 30f) * tw
            ink.color = withAlpha(0xFFFFFAE8.toInt(), (230 * tw).toInt()); ink.strokeWidth = .8f
            c.drawLine(x - r * 1.6f, y, x + r * 1.6f, y, ink); c.drawLine(x, y - r * .6f, x, y + r * .6f, ink)
        }
    }

    private fun sail(x: Float): Path {
        fringe.reset()
        fringe.moveTo(x, HORIZON - 3f); fringe.lineTo(x, HORIZON - 16f); fringe.lineTo(x + 7f, HORIZON - 3f); fringe.close()
        fringe.moveTo(x - 1f, HORIZON - 4f); fringe.lineTo(x - 1f, HORIZON - 13f); fringe.lineTo(x - 6f, HORIZON - 4f); fringe.close()
        return fringe
    }

    /** Une guêpe qui tourne autour de la canette, en huit. */
    private fun wasp(c: Canvas, t: Float) {
        val p = t * .9f
        val x = CX + 44f * sin(p) + 6f * sin(p * 3.1f)
        val y = 150f + 18f * sin(p * 2f) + 4f * cos(p * 4.3f)
        val dir = if (cos(p) >= 0f) 1f else -1f
        c.save(); c.translate(x, y); c.scale(dir, 1f); c.rotate(8f * sin(p * 2f))
        val flap = abs(sin(t * 40f))
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 150)
        c.drawOval(-4f, -6f - 3f * flap, 1f, -1f, fill)
        c.drawOval(-1f, -5.5f - 2f * flap, 4f, -1f, fill)
        fill.color = 0xFFE8B020.toInt()
        c.drawOval(-6f, -2f, 2f, 2.4f, fill)
        fill.color = Ink.SEPIA
        c.drawRect(-4.4f, -2f, -3.2f, 2.4f, fill); c.drawRect(-2f, -2f, -.8f, 2.4f, fill)
        c.drawCircle(3.6f, 0f, 2f, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = .5f
        c.drawLine(5f, -1f, 7f, -3.5f, ink)
        c.restore()
    }

    /** Le volant du parasol, en haut : des festons rayés qui ondulent au vent. */
    private fun parasol(c: Canvas, t: Float) {
        val w = 280f / FESTOONS
        for (k in 0 until FESTOONS) {
            val x0 = 40f + k * w
            val sway = 2.2f * sin(t * 2.2f + k * .7f)
            fringe.reset()
            fringe.moveTo(x0, 50f); fringe.lineTo(x0 + w, 50f); fringe.lineTo(x0 + w, PARASOL_Y)
            fringe.quadTo(x0 + w / 2f + sway, PARASOL_Y + 14f, x0, PARASOL_Y)
            fringe.close()
            fill.color = if (k % 2 == 0) 0xFFC8402E.toInt() else 0xFFF6F0E2.toInt()
            c.drawPath(fringe, fill)
            ink.color = Ink.SEPIA; ink.strokeWidth = .7f
            c.drawPath(fringe, ink)
        }
        fill.color = withAlpha(0xFF2A1D14.toInt(), 50)
        c.drawRect(40f, 50f, 320f, PARASOL_Y - 6f, fill)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val HORIZON = 184f
        const val CX = 196f
        const val CAN_R = 28f
        const val CAN_TOP = 142f
        const val CAN_BOTTOM = 270f
        const val GLASS_X = 122f
        const val GLASS_TOP = 222f
        const val GLASS_BOTTOM = 280f
        const val LIQUID_Y = 232f
        const val PARASOL_Y = 70f
        const val FESTOONS = 9
        const val DROP_PERIOD = 3.4f
    }
}
