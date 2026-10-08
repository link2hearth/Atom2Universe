package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.sin

/**
 * Platine — la pile à hydrogène. Sur une fine couche de grains de platine, l'hydrogène se coupe
 * en protons et en électrons. La pile en coupe : les molécules d'hydrogène arrivent à gauche et se
 * cassent sur le platine ; les protons traversent la membrane, les électrons font le tour par le
 * fil et allument l'ampoule ; à droite, ils retrouvent l'oxygène et forment des gouttes d'eau.
 */
internal class ScenePlatinum : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_fuel_cell
    override val noteRes = R.string.card_note_fuel_cell
    override val explainRes = R.string.card_explain_fuel_cell

    override fun engrave(b: Burin) {
        b.wash(rect(40f, 50f, 320f, 340f), 0xFFD8D4C8.toInt(), 255)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 90f, 4.4f, .4f, withAlpha(Ink.BROWN, 70))
        // Les deux canaux de gaz, la membrane, les couches de platine.
        b.body(rect(70f, TOP, ANODE, BOTTOM), 0xFFB8D0E8.toInt(), .2f, 90f, outline = .9f, washAlpha = 255)
        b.body(rect(CATHODE, TOP, 290f, BOTTOM), 0xFFE8D0C0.toInt(), .2f, 90f, outline = .9f, washAlpha = 255)
        b.body(rect(ANODE + 8f, TOP, CATHODE - 8f, BOTTOM), 0xFFF0E6B0.toInt(), .1f, 0f, outline = .9f, washAlpha = 230)
        for (x in floatArrayOf(ANODE, CATHODE - 8f)) {
            b.body(rect(x, TOP, x + 8f, BOTTOM), 0xFF5A5E66.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
            val rnd = kotlin.random.Random(x.toInt())
            repeat(60) { b.c.drawCircle(x + 1f + rnd.nextFloat() * 6f, TOP + rnd.nextFloat() * (BOTTOM - TOP), 1.1f, b.pen(0xFFE8EAEE.toInt())) }
        }
        // Les plaques collectrices, le fil et l'ampoule.
        b.body(rect(64f, TOP - 8f, 296f, TOP), 0xFF8A8E94.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.wash(rect(72f, TOP - 8f, 128f, TOP), 0xFF3A5A8A.toInt(), 120)
        b.wash(rect(232f, TOP - 8f, 288f, TOP), 0xFF8A3A2A.toInt(), 120)
        b.c.drawPath(android.graphics.Path().apply { moveTo(WX0, TOP - 8f); lineTo(WX0, WIRE_Y); lineTo(WX1, WIRE_Y); lineTo(WX1, TOP - 8f) }, b.pen(0xFFB87333.toInt(), 2.2f))
        b.body(ellipse(180f, WIRE_Y - 12f, 13f, 14f), 0xFFF4F0DC.toInt(), 0f, outline = .9f, washAlpha = 200)
        b.body(rect(174f, WIRE_Y - 1f, 186f, WIRE_Y + 7f), 0xFFB8A060.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        // Le tuyau d'eau en bas à droite.
        b.body(rect(250f, BOTTOM, 270f, 318f), 0xFF8A8E94.toInt(), .3f, 90f, outline = .8f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // L'ampoule brille, nourrie par le courant.
        val glow = .85f + .15f * sin(t * 9f)
        fill.color = withAlpha(0xFFFFE890.toInt(), (110 * glow).toInt()); c.drawCircle(180f, WIRE_Y - 12f, 28f, fill)
        fill.color = withAlpha(0xFFFFF8D0.toInt(), (230 * glow).toInt()); c.drawCircle(180f, WIRE_Y - 13f, 9f, fill)
        line.color = 0xFFFF9A30.toInt(); line.strokeWidth = 1f
        c.drawLine(176f, WIRE_Y - 8f, 184f, WIRE_Y - 14f, line)

        for (k in 0 until EVENTS) {
            val u = ((t / PERIOD + k / EVENTS.toFloat()) % 1f)
            val n = ((t / PERIOD + k / EVENTS.toFloat())).toInt()
            val y = TOP + 18f + (BOTTOM - TOP - 36f) * hash(k, n)
            // Une molécule d'hydrogène descend le canal et vient se casser sur le platine.
            if (u < .3f) {
                val f = u / .3f
                val x = 90f + (ANODE - 4f - 90f) * f; val yy = TOP + 10f + (y - TOP - 10f) * f
                val a = (255 * sin(max(f, .05f) * 1.5707964f)).toInt()
                atom(c, x - 3f, yy, 3.6f, 0xFFF4F6F8.toInt(), a); atom(c, x + 3f, yy, 3.6f, 0xFFF4F6F8.toInt(), a)
            }
            val split = 1f - kotlin.math.abs(u - .32f) / .04f
            if (split > 0f) { fill.color = withAlpha(Ink.WHITE, (220 * split).toInt()); c.drawCircle(ANODE + 4f, y, 6f * split + 2f, fill) }
            // Les deux protons traversent la membrane.
            if (u in .32f..0.8f) {
                val f = (u - .32f) / .48f
                val x = ANODE + 8f + (CATHODE - ANODE - 16f) * f
                val a = (255 * sin(f * 3.1415927f).coerceAtLeast(.3f)).toInt()
                atom(c, x, y - 4f + 2f * sin(f * 20f), 2.6f, 0xFFD8402E.toInt(), a)
                atom(c, x - 6f, y + 4f + 2f * sin(f * 17f + 1f), 2.6f, 0xFFD8402E.toInt(), a)
            }
            // Les deux électrons font le tour par le fil.
            if (u in .32f..0.92f) {
                val f = (u - .32f) / .6f
                for (e in 0 until 2) electron(c, (f - e * .06f).coerceIn(0f, 1f))
            }
            // L'oxygène arrive à droite ; l'eau se forme et s'écoule.
            if (u in .5f..0.8f) {
                val f = (u - .5f) / .3f
                val x = 272f + (CATHODE + 2f - 272f) * f; val yy = TOP + 10f + (y - TOP - 10f) * f
                val a = (255 * sin(max(f, .05f) * 1.5707964f)).toInt()
                atom(c, x - 3.5f, yy, 4f, 0xFFE05A3A.toInt(), a); atom(c, x + 3.5f, yy, 4f, 0xFFE05A3A.toInt(), a)
            }
            if (u > .8f) {
                val f = (u - .8f) / .2f
                val x = CATHODE + 6f + (260f - CATHODE - 6f) * f; val yy = y + (BOTTOM + 14f - y) * f * f
                val a = (255 * (1f - f * f)).toInt()
                atom(c, x, yy, 4f, 0xFFE05A3A.toInt(), a)
                atom(c, x - 4f, yy + 3f, 2.6f, 0xFFF4F6F8.toInt(), a); atom(c, x + 4f, yy + 3f, 2.6f, 0xFFF4F6F8.toInt(), a)
            }
        }
        // Les gouttes qui tombent du tuyau.
        for (k in 0 until 2) {
            val f = (t * .8f + k * .5f) % 1f
            fill.color = withAlpha(0xFF5AA0E0.toInt(), (220 * (1f - f)).toInt())
            c.drawCircle(260f, 318f + 30f * f * f, 2.6f, fill)
        }
    }

    /** Un électron le long du fil : il monte de la plaque de gauche, passe l'ampoule, redescend à droite. */
    private fun electron(c: Canvas, f: Float) {
        val rise = TOP - 8f - WIRE_Y
        val across = WX1 - WX0
        val d = f * (rise * 2f + across)
        val x: Float; val y: Float
        when {
            d < rise -> { x = WX0; y = TOP - 8f - d }
            d < rise + across -> { x = WX0 + d - rise; y = WIRE_Y }
            else -> { x = WX1; y = WIRE_Y + d - rise - across }
        }
        fill.color = withAlpha(0xFF3A8AFF.toInt(), 80); c.drawCircle(x, y, 4f, fill)
        fill.color = 0xFF2A6AE0.toInt(); c.drawCircle(x, y, 2f, fill)
    }

    private fun atom(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        fill.color = withAlpha(color, alpha); c.drawCircle(x, y, r, fill)
        line.color = withAlpha(Ink.SEPIA, alpha / 2); line.strokeWidth = .6f; c.drawCircle(x, y, r, line)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val TOP = 110f
        const val BOTTOM = 290f
        const val ANODE = 130f
        const val CATHODE = 230f
        const val WX0 = 100f
        const val WX1 = 260f
        const val WIRE_Y = 84f
        const val PERIOD = 3.6f
        const val EVENTS = 6
    }
}
