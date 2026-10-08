package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Rhodium — le pot catalytique. Sous la voiture, le pot catalytique est un nid d'abeille de
 * céramique couvert d'une pellicule de rhodium et de platine. Vu en coupe : les gaz sales du
 * moteur (monoxyde de carbone, oxydes d'azote) entrent par la gauche, traversent le nid
 * d'abeille brûlant, et ressortent changés en azote, gaz carbonique et vapeur d'eau.
 */
internal class SceneRhodium : EngravedScene() {

    override val sky = Sky.DAY
    override val nameRes = R.string.card_scene_catalytic_converter
    override val noteRes = R.string.card_note_catalytic_converter
    override val explainRes = R.string.card_explain_catalytic_converter

    private val can = Path().apply { addRoundRect(CAN_L, CAN_T, CAN_R, CAN_B, 26f, 26f, Path.Direction.CW) }
    private val core = rect(CORE_L, CAN_T + 10f, CORE_R, CAN_B - 10f)

    override fun engrave(b: Burin) {
        // Le bas de caisse de la voiture, en haut ; la route qui défile en bas (animée).
        val body = Path().apply {
            moveTo(40f, 50f); lineTo(320f, 50f); lineTo(320f, 120f); cubicTo(300f, 128f, 60f, 128f, 40f, 120f); close()
        }
        b.body(body, 0xFF2A2E34.toInt(), .45f, 0f, outline = 1f, washAlpha = 255)
        b.line(40f, 118f, 320f, 118f, 1f, withAlpha(0xFF8A929C.toInt(), 150))
        for (x in floatArrayOf(90f, 170f, 250f)) b.body(rect(x - 3f, 120f, x + 3f, PIPE_Y - 8f), 0xFF5A6068.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        val road = rect(40f, ROAD, 320f, 340f)
        b.wash(road, 0xFF5A5A5E.toInt(), 255)
        b.hatchRect(RectF(40f, ROAD, 320f, 340f), 0f, 1.8f, .4f, Ink.SEPIA, Fade(0f, ROAD, 0f, 330f, 80, 200))
        b.washGradient(rect(40f, ROAD, 320f, ROAD + 20f), 0xFF14100C.toInt(), 0f, ROAD, 160, 0f, ROAD + 20f, 0)
        // Le tuyau d'échappement, de part et d'autre du pot.
        for ((l, r) in arrayOf(40f to CAN_L + 4f, CAN_R - 4f to 306f)) {
            val pipe = rect(l, PIPE_Y - PIPE_R, r, PIPE_Y + PIPE_R)
            b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, PIPE_Y - PIPE_R, 0f, PIPE_Y + PIPE_R,
                intArrayOf(0xFF6A6058.toInt(), 0xFFC8BCB0.toInt(), 0xFF5A4A40.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            b.c.drawPath(pipe, b.p)
            b.stroke(pipe, .9f)
        }
        b.body(ellipse(306f, PIPE_Y, 4f, PIPE_R), 0xFF1A1410.toInt(), 0f, outline = .9f, washAlpha = 255)
        // Le pot ouvert en coupe : l'enveloppe d'acier, le nid d'abeille brûlant.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, CAN_T, 0f, CAN_B,
            intArrayOf(0xFF8A8078.toInt(), 0xFFD8D0C8.toInt(), 0xFF6A5E54.toInt()), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(can, b.p)
        b.stroke(can, 1.1f)
        val inside = Path().apply { addRoundRect(CAN_L + 6f, CAN_T + 6f, CAN_R - 6f, CAN_B - 6f, 20f, 20f, Path.Direction.CW) }
        b.fill(inside, 0xFF3A2A22.toInt())
        b.pen(0xFF000000.toInt()).shader = RadialGradient((CORE_L + CORE_R) / 2f, PIPE_Y, 70f,
            intArrayOf(0xFFFFB050.toInt(), 0xFFD86A2A.toInt(), 0xFF8A3A1A.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(core, b.p)
        // Les canaux du nid d'abeille : une grille fine.
        b.c.save(); b.c.clipPath(core)
        var x = CORE_L
        while (x <= CORE_R) { b.line(x, CAN_T, x, CAN_B, .7f, withAlpha(0xFF5A2A10.toInt(), 200)); x += 5f }
        var y = CAN_T + 10f
        while (y <= CAN_B) { b.line(CORE_L, y, CORE_R, y, .9f, withAlpha(0xFF5A2A10.toInt(), 220)); y += 6f }
        b.c.restore()
        b.stroke(core, .9f)
        // Les cônes d'entrée et de sortie.
        b.line(CAN_L + 6f, PIPE_Y - PIPE_R, CORE_L, CAN_T + 10f, .8f, withAlpha(Ink.SEPIA, 200))
        b.line(CAN_L + 6f, PIPE_Y + PIPE_R, CORE_L, CAN_B - 10f, .8f, withAlpha(Ink.SEPIA, 200))
        b.line(CAN_R - 6f, PIPE_Y - PIPE_R, CORE_R, CAN_T + 10f, .8f, withAlpha(Ink.SEPIA, 200))
        b.line(CAN_R - 6f, PIPE_Y + PIPE_R, CORE_R, CAN_B - 10f, .8f, withAlpha(Ink.SEPIA, 200))
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La route défile sous la voiture.
        val shift = (t * 140f) % 60f
        ink.color = 0xFFE8E4D8.toInt(); ink.strokeWidth = 3f
        var x = 40f - shift
        while (x < 330f) { c.drawLine(x, ROAD + 34f, x + 30f, ROAD + 34f, ink); x += 60f }
        // Le nid d'abeille respire un peu, rouge et chaud.
        fill.color = withAlpha(0xFFFFE0A0.toInt(), (40 + 30 * sin(t * 1.7f)).toInt())
        c.drawRect(CORE_L, CAN_T + 10f, CORE_R, CAN_B - 10f, fill)
        // Les molécules : sales avant le pot, propres après ; elles avancent avec le flux.
        for (k in 0 until FLOW) {
            val speed = 46f + 10f * hash(k, 1)
            val x0 = ((hash(k, 2) * 300f + t * speed) % 300f) + 30f
            val lane = (hash(k, 3) - .5f)
            // Le flux s'élargit dans le cône d'entrée et se resserre dans celui de sortie.
            val open = when {
                x0 < CAN_L + 6f -> 0f
                x0 < CORE_L -> (x0 - CAN_L - 6f) / (CORE_L - CAN_L - 6f)
                x0 < CORE_R -> 1f
                x0 < CAN_R - 6f -> 1f - (x0 - CORE_R) / (CAN_R - 6f - CORE_R)
                else -> 0f
            }
            val half = PIPE_R - 3f + open * ((CAN_B - CAN_T) / 2f - 16f - PIPE_R + 3f)
            val y = PIPE_Y + lane * 2f * half + 1.5f * sin(t * 3f + k)
            if (x0 < CORE_L + 10f) dirty(c, x0, y, k) else if (x0 > CORE_R - 10f) clean(c, x0, y, k)
            else {
                // Dans le nid d'abeille : on devine la molécule qui change, un éclat.
                fill.color = withAlpha(0xFFFFF4D0.toInt(), 200)
                c.drawCircle(x0, y, 1.6f, fill)
            }
        }
        // À la sortie du pot d'échappement, un peu de chaleur qui tremble.
        for (k in 0 until 4) {
            val life = ((t * 1.2f + k / 4f) % 1f)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (40 * (1f - life)).toInt())
            c.drawCircle(312f + life * 18f, PIPE_Y + 3f * sin(life * 8f + k), 3f + life * 6f, fill)
        }
    }

    /** Une molécule sale : CO (noir et rouge) ou NO (bleu et rouge). */
    private fun dirty(c: Canvas, x: Float, y: Float, k: Int) {
        val a = if (k % 2 == 0) CARBON else NITROGEN
        ball(c, x - 2.4f, y, a); ball(c, x + 2.4f, y, OXYGEN)
        fill.color = withAlpha(0xFF6A5A4A.toInt(), 60); c.drawCircle(x, y, 7f, fill)
    }

    /** Une molécule propre : CO₂ (rouge, noir, rouge), N₂ (deux bleus) ou H₂O (rouge et deux blancs). */
    private fun clean(c: Canvas, x: Float, y: Float, k: Int) {
        when (k % 3) {
            0 -> { ball(c, x - 4.4f, y, OXYGEN); ball(c, x, y, CARBON); ball(c, x + 4.4f, y, OXYGEN) }
            1 -> { ball(c, x - 2.2f, y, NITROGEN); ball(c, x + 2.2f, y, NITROGEN) }
            else -> { ball(c, x, y, OXYGEN); ball(c, x - 3f, y + 2.4f, HYDROGEN, 1.8f); ball(c, x + 3f, y + 2.4f, HYDROGEN, 1.8f) }
        }
    }

    private fun ball(c: Canvas, x: Float, y: Float, color: Int, r: Float = 2.6f) {
        fill.color = color; c.drawCircle(x, y, r, fill)
        ink.color = withAlpha(Ink.SEPIA, 200); ink.strokeWidth = .5f; c.drawCircle(x, y, r, ink)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 150); c.drawCircle(x - r * .3f, y - r * .3f, r * .35f, fill)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val ROAD = 270f
        const val PIPE_Y = 196f
        const val PIPE_R = 9f
        const val CAN_L = 106f
        const val CAN_R = 254f
        const val CAN_T = 156f
        const val CAN_B = 236f
        const val CORE_L = 134f
        const val CORE_R = 226f
        const val FLOW = 34
        const val CARBON = 0xFF2A2A2E.toInt()
        const val OXYGEN = 0xFFD83A2A.toInt()
        const val NITROGEN = 0xFF3A6AD8.toInt()
        const val HYDROGEN = 0xFFF4F4F0.toInt()
    }
}
