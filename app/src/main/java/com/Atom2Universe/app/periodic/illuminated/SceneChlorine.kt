package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Chlore — la piscine. Une petite dose de chlore tue les microbes et garde l'eau claire : on
 * voit le carrelage du fond à travers, sous le réseau de lumière qui danse. Un nageur fait des
 * longueurs entre les lignes d'eau.
 */
internal class SceneChlorine : EngravedScene() {

    override val nameRes = R.string.card_scene_swimming_pool
    override val noteRes = R.string.card_note_swimming_pool
    override val explainRes = R.string.card_explain_swimming_pool

    override fun engrave(b: Burin) {
        backdrop(b)
        pool(b)
        nearEdge(b)
    }

    private fun backdrop(b: Burin) {
        // Les peupliers derrière les cabines.
        for ((x, h) in listOf(58f to 74f, 74f to 62f, 250f to 70f, 270f to 58f, 300f to 78f)) {
            val tree = ellipse(x, 134f - h / 2f, 8f, h / 2f)
            b.body(tree, 0xFF4E6E3C.toInt(), .5f, 70f, Fade(x + 8f, 0f, x - 8f, 0f, 255, 40), outline = .6f, washAlpha = 220)
        }
        // La rangée de cabines, murs blancs et portes bleues.
        b.body(rect(90f, 108f, 240f, 136f), 0xFFF4EEE2.toInt(), .2f, 90f, Fade(0f, 108f, 0f, 136f, 0, 160), outline = .7f, washAlpha = 255)
        b.body(rect(86f, 104f, 244f, 109f), 0xFFB0523A.toInt(), .35f, 0f, outline = .6f, washAlpha = 255)
        var x = 96f
        while (x < 234f) {
            b.body(rect(x, 116f, x + 7f, 136f), 0xFF3E78B0.toInt(), .3f, 90f, outline = .5f, washAlpha = 255)
            x += 12f
        }
        // Le plongeoir à gauche, la chaise du maître nageur à droite.
        b.body(rect(64f, 132f, 70f, 150f), 0xFFE8E2D6.toInt(), .3f, 90f, outline = .6f, washAlpha = 255)
        b.body(poly(58f, 130f, 112f, 136f, 112f, 139f, 58f, 133f), 0xFFE8E2D6.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        for (lx in floatArrayOf(254f, 266f)) b.line(lx, 150f, lx + (if (lx < 260f) 3f else -3f), 112f, 1.4f, 0xFFF4F4F0.toInt())
        for (lx in floatArrayOf(254f, 266f)) b.line(lx, 150f, lx + (if (lx < 260f) 3f else -3f), 112f, .4f)
        for (ly in floatArrayOf(124f, 136f)) b.line(256f, ly, 264f, ly, .8f)
        b.body(rect(254f, 108f, 268f, 112f), 0xFFC84A3A.toInt(), .3f, 0f, outline = .5f, washAlpha = 255)
        // La plage de carrelage.
        val deck = rect(40f, 136f, 320f, 156f)
        b.body(deck, 0xFFE6D8BC.toInt(), .15f, 0f, outline = 0f, washAlpha = 255)
        for (k in 0..4) b.line(40f, 138f + k * 4.2f, 320f, 138f + k * 4.2f, .4f, withAlpha(Ink.BROWN, 150))
        var j = -10
        while (j < 40) {
            val xa = 180f + j * 9f; b.line(xa, 136f, 180f + j * 10.5f, 156f, .4f, withAlpha(Ink.BROWN, 150)); j++
        }
        // La margelle du fond.
        b.body(rect(40f, 155f, 320f, 160f), 0xFFF6F2EA.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
    }

    private fun pool(b: Burin) {
        val water = rect(40f, WATER, 320f, 340f)
        b.washGradient(water, 0xFF6CCAD2.toInt(), 0f, WATER, 255, 0f, 330f, 255)
        b.washGradient(water, 0xFF1E7FA0.toInt(), 0f, WATER, 0, 0f, 330f, 220)
        // Le carrelage du fond, vu à travers l'eau, en perspective.
        val tile = b.pen(withAlpha(0xFF1E6A80.toInt(), 80), .45f)
        for (k in 0 until 18) {
            val y = WATER + 2f + 170f * (k / 18f).pow(1.5f)
            b.c.drawLine(40f, y, 320f, y, tile)
        }
        for (k in -30..30) b.c.drawLine(180f + k * 6f, WATER, 180f + k * 16f, 340f, tile)
        // Les bandes sombres au fond de chaque couloir.
        for (i in 0 until ROPES.size - 1) {
            val mid = (ROPES[i] + ROPES[i + 1]) / 2f + 3f
            val w = (ROPES[i + 1] - ROPES[i]) * .16f
            b.c.drawRect(66f, mid - w / 2f, 294f, mid + w / 2f, b.pen(withAlpha(0xFF173E66.toInt(), 150)))
            b.c.drawRect(66f, mid - w * 1.6f, 66f + w * .8f, mid + w * 1.6f, b.pen(withAlpha(0xFF173E66.toInt(), 150)))
            b.c.drawRect(294f - w * .8f, mid - w * 1.6f, 294f, mid + w * 1.6f, b.pen(withAlpha(0xFF173E66.toInt(), 150)))
        }
        // La surface : des rides en hachures claires.
        b.hatch(water, 0f, 2.2f, .4f, withAlpha(0xFFFFFFFF.toInt(), 120), Fade(0f, WATER, 0f, 300f, 200, 30), wobble = .5f)
        // Les lignes d'eau : bouchons rouges et blancs, plus gros vers nous.
        for (y in ROPES) {
            val s = .6f + (y - WATER) / 70f
            var x = 40f + (y % 7f)
            var k = 0
            b.line(40f, y, 320f, y, .5f * s, withAlpha(Ink.SEPIA, 150))
            while (x < 322f) {
                val red = (k / 3) % 2 == 0
                b.c.drawOval(x - 2.2f * s, y - 1.5f * s, x + 2.2f * s, y + 1.5f * s, b.pen(if (red) 0xFFD23A2E.toInt() else 0xFFF8F6F0.toInt()))
                b.c.drawOval(x - 2.2f * s, y - 1.5f * s, x + 2.2f * s, y + 1.5f * s, b.pen(Ink.SEPIA, .4f))
                x += 5f * s; k++
            }
        }
    }

    /** La margelle de devant et l'échelle chromée qui descend dans l'eau. */
    private fun nearEdge(b: Burin) {
        val coping = rect(40f, 306f, 320f, 340f)
        b.body(coping, 0xFFF0EADC.toInt(), .2f, 0f, Fade(0f, 306f, 0f, 320f, 230, 0), outline = .8f, washAlpha = 255)
        var x = 40f
        while (x < 320f) { b.line(x, 306f, x - 4f, 340f, .5f); x += 30f }
        for (rx in floatArrayOf(258f, 286f)) {
            val rail = Path().apply { moveTo(rx, 320f); cubicTo(rx, 296f, rx - 2f, 288f, rx - 12f, 288f); cubicTo(rx - 20f, 288f, rx - 20f, 296f, rx - 20f, 304f) }
            b.stroke(rail, 3.4f, Ink.SEPIA)
            b.stroke(rail, 2f, 0xFFD8DCE2.toInt())
            b.stroke(rail, .6f, 0xFFFFFFFF.toInt())
        }
        for (ry in floatArrayOf(296f, 302f)) b.line(240f, ry, 266f, ry, 1.2f, withAlpha(0xFFD8DCE2.toInt(), 160))
    }

    // ───────────── animation ─────────────

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pts = FloatArray(2400)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        c.save(); c.clipRect(40f, WATER, 320f, 306f)
        caustics(c, t)
        swimmer(c, t)
        // Des éclats de soleil sur l'eau.
        val frame = (t * 5f).toInt()
        ink.strokeWidth = .8f
        for (i in 0 until 22) {
            val h = hash(i, frame)
            if (h < .6f) continue
            val x = 46f + hash(i, 999) * 268f
            val y = WATER + 4f + hash(i, 777).pow(1.6f) * 140f
            val r = (h - .6f) * 9f
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * (h - .6f) / .4f).toInt())
            c.drawLine(x - r, y, x + r, y, ink); c.drawLine(x, y - r * .6f, x, y + r * .6f, ink)
        }
        c.restore()
    }

    /** Le réseau de lumière qui danse sur le fond : des lignes ondulées en travers et en long. */
    private fun caustics(c: Canvas, t: Float) {
        var n = 0
        for (r in 0 until 12) {
            val y0 = WATER + 4f + 150f * (r / 12f).pow(1.35f)
            val depth = (y0 - WATER) / 150f
            val amp = 1.2f + depth * 3f
            var lx = 40f; var ly = y0 + amp * sin(40f * .07f + t * 1.3f + r)
            var x = 54f
            while (x <= 324f) {
                val y = y0 + amp * sin(x * .07f + t * 1.3f + r) + amp * .6f * sin(x * .13f - t * 1.7f + r * 2f)
                pts[n++] = lx; pts[n++] = ly; pts[n++] = x; pts[n++] = y
                lx = x; ly = y; x += 14f
            }
        }
        for (k in 0 until 18) {
            val xc = 40f + k * 16.5f
            var lx = xc; var ly = WATER
            var y = WATER + 10f
            while (y <= 312f) {
                val depth = (y - WATER) / 150f
                val x = xc + (2f + depth * 4f) * sin(y * .09f + t * 1.1f + k) + 2f * sin(y * .05f - t * 1.6f + k * 1.7f)
                pts[n++] = lx; pts[n++] = ly; pts[n++] = x; pts[n++] = y
                lx = x; ly = y; y += 10f + depth * 6f
            }
        }
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 70); ink.strokeWidth = 1.1f
        c.drawLines(pts, 0, n, ink)
    }

    /** Le nageur, en crawl, d'un bout à l'autre de son couloir. */
    private fun swimmer(c: Canvas, t: Float) {
        val lane = (ROPES[3] + ROPES[4]) / 2f + 2f
        c.save(); c.translate((t * 30f) % 440f - 60f, lane + .8f * sin(t * 5f)); c.scale(1.35f, 1.35f)
        val x = 0f
        val y = 0f
        // L'ombre sur le fond, décalée.
        fill.color = withAlpha(0xFF0E3A50.toInt(), 70)
        c.drawOval(x - 44f, y + 9f, x + 2f, y + 15f, fill)
        // Le sillage.
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 120); ink.strokeWidth = .8f
        for (k in 0..2) {
            val d = 8f + k * 10f
            c.drawLine(x - 44f - d, y - 2f - k * 1.6f, x - 52f - d, y - 3f - k * 2.2f, ink)
            c.drawLine(x - 44f - d, y + 2f + k * 1.6f, x - 52f - d, y + 3f + k * 2.2f, ink)
        }
        // Le corps et les jambes, sous l'eau.
        val kick = sin(t * 14f)
        ink.color = withAlpha(0xFFB88A72.toInt(), 190); ink.strokeWidth = 3.4f
        c.drawLine(x - 30f, y, x - 46f, y - 1.5f + kick * 1.6f, ink)
        c.drawLine(x - 30f, y, x - 46f, y + 1.5f - kick * 1.6f, ink)
        fill.color = withAlpha(0xFFC89A80.toInt(), 200)
        c.drawOval(x - 34f, y - 3.6f, x - 2f, y + 3.6f, fill)
        fill.color = withAlpha(0xFF1E3A70.toInt(), 200)
        c.drawOval(x - 34f, y - 3.4f, x - 24f, y + 3.4f, fill)
        // Les battements font mousser derrière.
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 200)
        for (k in 0 until 4) {
            val bx = x - 48f - k * 3f + 2f * sin(t * 20f + k)
            c.drawCircle(bx, y + 1.5f * sin(t * 17f + k * 2f), 1f + .6f * (k % 2), fill)
        }
        // Les bras : l'un tire sous l'eau, l'autre revient par-dessus.
        val phase = t * 4.2f
        for (k in 0..1) {
            val a = phase + k * PI.toFloat()
            val hx = x - 4f + cos(a) * 15f; val hy = y - sin(a) * 9f
            val over = sin(a) > 0f
            val ex = x - 4f + cos(a) * 7f; val ey = y - sin(a) * 9f - (if (over) 3f else 0f)
            ink.color = withAlpha(0xFFC89A80.toInt(), if (over) 255 else 130); ink.strokeWidth = 2.6f
            c.drawLine(x - 6f, y, ex, ey, ink); c.drawLine(ex, ey, hx, hy, ink)
            if (over) { ink.color = Ink.SEPIA; ink.strokeWidth = .5f; c.drawLine(x - 6f, y - 1.3f, ex, ey - 1.3f, ink) }
            // La main qui rentre dans l'eau éclabousse.
            if (cos(a) > .85f && sin(a) < .3f && sin(a) > -.3f) {
                fill.color = withAlpha(0xFFFFFFFF.toInt(), 230)
                c.drawCircle(hx + 2f, y - 2f, 1.6f, fill); c.drawCircle(hx - 1f, y - 3.5f, 1.1f, fill); c.drawCircle(hx + 4f, y - 3f, .9f, fill)
            }
        }
        // La tête, bonnet rouge et lunettes, et la vague d'étrave.
        fill.color = 0xFFC89A80.toInt(); c.drawCircle(x + 2f, y - .5f, 4.2f, fill)
        fill.color = 0xFFD0402E.toInt(); c.drawArc(x - 2.4f, y - 4.9f, x + 6.4f, y + 3.9f, 180f, 200f, true, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = .6f; c.drawCircle(x + 2f, y - .5f, 4.2f, ink)
        c.drawLine(x + 2f, y + .4f, x + 6f, y + .4f, ink)
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 220); ink.strokeWidth = .9f
        c.drawArc(x + 2f, y - 4f, x + 10f, y + 4f, 300f, 120f, false, ink)
        c.restore()
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val WATER = 160f
        /** Les lignes d'eau, du fond vers nous. */
        val ROPES = floatArrayOf(168f, 182f, 202f, 230f, 268f)
    }
}
