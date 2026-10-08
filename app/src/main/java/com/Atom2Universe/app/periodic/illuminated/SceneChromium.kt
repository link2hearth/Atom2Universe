package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Chrome — le robinet chromé. Une couche de chrome, mince comme un cheveu, rend le métal
 * brillant comme un miroir et le protège de la rouille. Le mitigeur au col de cygne coule dans
 * la vasque : l'eau file, éclabousse et fait des ronds autour de la bonde ; un reflet glisse le
 * long du chrome.
 */
internal class SceneChromium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_chrome_tap
    override val noteRes = R.string.card_note_chrome_tap
    override val explainRes = R.string.card_explain_chrome_tap

    private val spout = Path().apply {
        moveTo(COL_X, COL_TOP + 2f)
        cubicTo(COL_X, 76f, STREAM_X, 70f, STREAM_X, SPOUT_END)
    }
    private val basin = ellipse(BASIN_X, BASIN_Y, 104f, 40f)

    /** Des points le long du col de cygne, pour y faire glisser le reflet. */
    private val spoutX = FloatArray(SPOUT_N)
    private val spoutY = FloatArray(SPOUT_N)

    init {
        val pm = PathMeasure(spout, false)
        val pos = FloatArray(2)
        for (i in 0 until SPOUT_N) {
            pm.getPosTan(pm.length * i / (SPOUT_N - 1), pos, null)
            spoutX[i] = pos[0]; spoutY[i] = pos[1]
        }
    }

    override fun engrave(b: Burin) {
        wall(b)
        counter(b)
        tap(b)
        stream(b)
    }

    /** Le carrelage, en grands carreaux, éclairé par une fenêtre à gauche. */
    private fun wall(b: Burin) {
        val wall = rect(40f, 50f, 320f, COUNTER)
        b.wash(wall, 0xFFD4DCE0.toInt(), 255)
        b.glow(80f, 110f, 180f, 0xFFFFF8EC.toInt(), 170)
        b.washGradient(wall, 0xFF4A5A66.toInt(), 0f, 110f, 0, 0f, COUNTER, 120)
        val grout = b.pen(0xFF9AA4AA.toInt(), .8f)
        var y = COUNTER - 30f
        while (y > 50f) { b.c.drawLine(40f, y, 320f, y, grout); y -= 30f }
        var x = 46f
        while (x < 320f) { b.c.drawLine(x, 50f, x, COUNTER, grout); x += 30f }
        b.hatchRect(RectF(40f, 50f, 320f, COUNTER), 90f, 3f, .35f, Ink.BROWN, Fade(0f, 110f, 0f, COUNTER, 20, 120))
    }

    /** Le plan de marbre vert, la vasque blanche creusée dedans, l'eau qui stagne autour de la bonde. */
    private fun counter(b: Burin) {
        val top = rect(40f, COUNTER, 320f, 340f)
        b.wash(top, 0xFF2E4A42.toInt(), 255)
        b.washGradient(top, 0xFF8AB0A0.toInt(), 0f, COUNTER, 160, 0f, 250f, 0)
        val rnd = Random(4)
        val vein = b.pen(withAlpha(0xFFC8DCD0.toInt(), 120), .5f)
        repeat(10) {
            val x0 = 40f + rnd.nextFloat() * 280f; val y0 = COUNTER + 4f + rnd.nextFloat() * 120f
            b.c.drawPath(Path().apply {
                moveTo(x0, y0); cubicTo(x0 + 20f, y0 + rnd.nextFloat() * 10f - 5f, x0 + 40f, y0 + rnd.nextFloat() * 12f - 6f, x0 + 70f, y0 + rnd.nextFloat() * 10f - 5f)
            }, vein)
        }
        b.hatchRect(RectF(40f, COUNTER, 320f, 340f), 0f, 2.2f, .4f, Ink.SEPIA, Fade(0f, COUNTER, 0f, 330f, 60, 200))
        b.line(40f, COUNTER, 320f, COUNTER, 1f)
        // La vasque : son rebord, l'intérieur blanc ombré vers le fond.
        b.c.drawOval(BASIN_X - 108f, BASIN_Y - 43f, BASIN_X + 108f, BASIN_Y + 43f, b.pen(0xFFF4F2EC.toInt()))
        b.c.drawOval(BASIN_X - 108f, BASIN_Y - 43f, BASIN_X + 108f, BASIN_Y + 43f, b.pen(Ink.SEPIA, .9f))
        b.wash(basin, 0xFFF8F6F0.toInt(), 255)
        b.washGradient(basin, 0xFF8A9298.toInt(), 0f, BASIN_Y - 40f, 170, 0f, BASIN_Y + 10f, 0)
        b.hatch(basin, 0f, 2f, .4f, Ink.SEPIA, Fade(0f, BASIN_Y - 40f, 0f, BASIN_Y - 6f, 170, 0))
        b.stroke(basin, 1f)
        // L'eau autour de la bonde, et la bonde chromée.
        b.body(ellipse(BASIN_X, BASIN_Y + 10f, 42f, 11f), 0xFFA8CCDC.toInt(), .15f, 0f, outline = .5f, washAlpha = 150)
        b.c.drawOval(BASIN_X - 9f, BASIN_Y + 7.5f, BASIN_X + 9f, BASIN_Y + 12.5f, b.pen(0xFFC8CED4.toInt()))
        b.c.drawOval(BASIN_X - 9f, BASIN_Y + 7.5f, BASIN_X + 9f, BASIN_Y + 12.5f, b.pen(Ink.SEPIA, .7f))
        b.c.drawOval(BASIN_X - 5f, BASIN_Y + 9f, BASIN_X + 5f, BASIN_Y + 11f, b.pen(0xFF2A2E32.toInt()))
    }

    /** Le mitigeur : son embase, la colonne, le col de cygne, la manette. */
    private fun tap(b: Burin) {
        b.fill(ellipse(COL_X + 6f, COUNTER + 9f, 24f, 4f), withAlpha(0xFF0E1A16.toInt(), 120))
        val flange = ellipse(COL_X, COUNTER + 6f, 18f, 5f)
        chrome(b, flange, COL_X - 18f, COL_X + 18f)
        b.stroke(flange, .8f)
        val column = rect(COL_X - 10f, COL_TOP, COL_X + 10f, COUNTER + 6f)
        chrome(b, column, COL_X - 10f, COL_X + 10f)
        b.stroke(column, .9f)
        // Le col de cygne : des bandes concentriques font le tube chromé.
        b.stroke(spout, 15f, Ink.SEPIA)
        b.stroke(spout, 13.4f, 0xFFA8AEB4.toInt())
        b.stroke(spout, 9f, 0xFF3A3E44.toInt())
        b.stroke(spout, 5.6f, 0xFF8A9096.toInt())
        b.c.save(); b.c.translate(-1.6f, -1.8f); b.stroke(spout, 2.6f, 0xFFFFFFFF.toInt()); b.c.restore()
        // L'aérateur au bout.
        val nozzle = rect(STREAM_X - 7f, SPOUT_END - 2f, STREAM_X + 7f, SPOUT_END + 8f)
        chrome(b, nozzle, STREAM_X - 7f, STREAM_X + 7f)
        b.stroke(nozzle, .8f)
        b.line(STREAM_X - 7f, SPOUT_END + 5f, STREAM_X + 7f, SPOUT_END + 5f, .5f)
        // La manette, sur le côté.
        val lever = limb(COL_X - 8f, 160f, COL_X - 34f, 148f, 7f, 5f)
        chrome(b, lever, COL_X - 34f, COL_X - 8f)
        b.stroke(lever, .8f)
        b.c.drawCircle(COL_X - 10f, 160f, 5.4f, b.pen(0xFFE8ECEE.toInt()))
        b.c.drawCircle(COL_X - 10f, 160f, 5.4f, b.pen(Ink.SEPIA, .8f))
        b.c.drawCircle(COL_X - 11.4f, 158.6f, 1.6f, b.pen(0xFFFFFFFF.toInt()))
    }

    /** Le chrome d'une pièce droite : des bandes franches, noires et blanches, comme un miroir du décor. */
    private fun chrome(b: Burin, shape: Path, x0: Float, x1: Float) {
        b.pen(0xFF000000.toInt()).shader = LinearGradient(x0, 0f, x1, 0f,
            intArrayOf(0xFF2A2E32.toInt(), 0xFFF4F6F8.toInt(), 0xFFB8BEC4.toInt(), 0xFF3A3E44.toInt(), 0xFF9AA0A6.toInt(), 0xFFFFFFFF.toInt(), 0xFF4A5056.toInt()),
            floatArrayOf(0f, .18f, .32f, .55f, .75f, .88f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(shape, b.p)
    }

    /** Le filet d'eau, posé une fois : transparent, plus clair au centre. */
    private fun stream(b: Burin) {
        val water = Path().apply {
            moveTo(STREAM_X - 4f, SPOUT_END + 8f); lineTo(STREAM_X + 4f, SPOUT_END + 8f)
            lineTo(STREAM_X + 2.8f, IMPACT_Y); lineTo(STREAM_X - 2.8f, IMPACT_Y); close()
        }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(STREAM_X - 4f, 0f, STREAM_X + 4f, 0f,
            intArrayOf(0x88A8D0E0.toInt(), 0xEEF4FCFF.toInt(), 0x99C8E4F0.toInt(), 0x667AA8C0), null, Shader.TileMode.CLAMP)
        b.c.drawPath(water, b.p)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les filets clairs qui descendent dans l'eau qui coule.
        val len = IMPACT_Y - SPOUT_END - 8f
        for (k in 0 until 6) {
            val y = SPOUT_END + 8f + ((t * 230f + k * len / 6f) % len)
            val x = STREAM_X + (k % 3 - 1) * 1.4f + .5f * sin(t * 11f + k)
            ink.color = withAlpha(0xFFFFFFFF.toInt(), 220); ink.strokeWidth = .9f
            c.drawLine(x, y, x, (y + 9f).coerceAtMost(IMPACT_Y), ink)
        }
        // Les ronds dans l'eau autour de l'impact.
        for (k in 0 until 3) {
            val life = (t / .9f + k / 3f) % 1f
            val rx = 6f + life * 34f
            ink.color = withAlpha(0xFF5A8AA8.toInt(), (200 * (1f - life)).toInt()); ink.strokeWidth = .8f
            c.drawOval(STREAM_X - rx, IMPACT_Y - rx * .26f, STREAM_X + rx, IMPACT_Y + rx * .26f, ink)
        }
        // L'éclaboussure : des gouttes qui sautent et retombent.
        for (k in 0 until 8) {
            val period = .55f + .25f * hash(k, 1)
            val cycle = t / period + hash(k, 2)
            val n = cycle.toInt()
            val u = cycle - n
            val dir = (hash(n, k + 7) - .5f) * 2f
            val x = STREAM_X + dir * 16f * u
            val y = IMPACT_Y - 2f - (18f + 10f * hash(n, k)) * (u * (1f - u) * 4f)
            fill.color = withAlpha(0xFFF4FCFF.toInt(), (240 * (1f - u * .5f)).toInt())
            c.drawCircle(x, y, 1.1f, fill)
        }
        // Un bourrelet d'eau au pied du filet.
        fill.color = withAlpha(0xFFF4FCFF.toInt(), 200)
        c.drawOval(STREAM_X - 5f - sin(t * 13f), IMPACT_Y - 2f, STREAM_X + 5f + sin(t * 11f), IMPACT_Y + 1.6f, fill)
        // Un reflet glisse le long du col de cygne, puis brille au sommet de la colonne.
        val g = (t / GLINT_PERIOD) % 1f
        val i = (g * (SPOUT_N - 1)).toInt().coerceIn(0, SPOUT_N - 1)
        val a = sin(g * PI.toFloat())
        val gx = spoutX[i] - 1.6f; val gy = spoutY[i] - 1.8f
        fill.color = withAlpha(0xFFFFFFFF.toInt(), (120 * a).toInt())
        c.drawCircle(gx, gy, 6f, fill)
        ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * a).toInt()); ink.strokeWidth = .9f
        val r = 7f * a
        c.drawLine(gx - r, gy, gx + r, gy, ink); c.drawLine(gx, gy - r, gx, gy + r, ink)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val COUNTER = 212f
        const val COL_X = 136f
        const val COL_TOP = 122f
        const val STREAM_X = 196f
        const val SPOUT_END = 112f
        const val BASIN_X = 196f
        const val BASIN_Y = 262f
        const val IMPACT_Y = 272f
        const val SPOUT_N = 60
        const val GLINT_PERIOD = 4.5f
    }
}
