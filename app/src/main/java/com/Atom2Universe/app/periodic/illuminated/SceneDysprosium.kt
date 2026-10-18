package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dysprosium — l'éolienne en mer. Le dysprosium garde leur force aux aimants qui chauffent, ceux
 * des génératrices d'éoliennes et des moteurs électriques. Au large, les éoliennes tournent ;
 * sur la plus proche, une découpe montre l'anneau d'aimants qui tourne avec les pales devant
 * les bobines, et le courant qui descend le mât.
 */
internal class SceneDysprosium : EngravedScene() {

    override val sky = Sky.DAY
    override val nameRes = R.string.card_scene_offshore_wind
    override val noteRes = R.string.card_note_offshore_wind
    override val explainRes = R.string.card_explain_offshore_wind

    override fun engrave(b: Burin) {
        // La mer jusqu'à l'horizon.
        val sea = rect(40f, HORIZON, 320f, 340f)
        b.body(sea, 0xFF3A7AA0.toInt(), .2f, 0f, outline = 0f, washAlpha = 255)
        b.hatchRect(RectF(40f, HORIZON, 320f, 340f), 0f, 3.2f, .5f, withAlpha(0xFF0A2A44.toInt(), 150), Fade(0f, HORIZON, 0f, 340f, 60, 200))
        b.line(40f, HORIZON, 320f, HORIZON, .8f, 0xFF2A4A60.toInt())
        // Les éoliennes du fond : mât blanc, nacelle.
        for (k in FAR_X.indices) {
            val x = FAR_X[k]; val h = FAR_H[k]
            b.body(poly(x - 1.6f, HORIZON + 4f, x + 1.6f, HORIZON + 4f, x + 1f, HORIZON - h, x - 1f, HORIZON - h), 0xFFF0F0EC.toInt(), 0f, outline = .4f, washAlpha = 255)
            b.body(rect(x - 3f, HORIZON - h - 2f, x + 2f, HORIZON - h + 2f), 0xFFF0F0EC.toInt(), 0f, outline = .4f, washAlpha = 255)
        }
        // L'éolienne proche : socle jaune, mât effilé, la découpe de la génératrice.
        b.body(rect(HX - 9f, 270f, HX + 9f, 300f), 0xFFE8C020.toInt(), .2f, 90f, outline = .8f, washAlpha = 255)
        b.body(rect(HX - 14f, 268f, HX + 14f, 272f), 0xFF8A8E94.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        val mast = poly(HX - 7f, 270f, HX + 7f, 270f, HX + 4f, HY + 10f, HX - 4f, HY + 10f)
        b.body(mast, 0xFFF4F4F0.toInt(), .2f, 90f, fade = Fade(HX + 7f, 0f, HX - 2f, 0f, 220, 0), outline = .8f, washAlpha = 255)
        b.body(ellipse(HX, HY, 36f, 36f), 0xFFE8E8E4.toInt(), .15f, 90f, outline = .8f, washAlpha = 255)
        b.body(ellipse(HX, HY, 33f, 33f), 0xFF2A3038.toInt(), 0f, outline = 0f, washAlpha = 255)
        // Les bobines fixes, tout autour.
        for (k in 0 until COILS) {
            val a = k * 6.2831855f / COILS
            b.c.drawCircle(HX + 30f * cos(a), HY + 30f * sin(a), 2.4f, b.pen(0xFFB87333.toInt()))
        }
        b.c.drawCircle(HX, HY, 34.5f, b.pen(withAlpha(Gilding.LEAF, 230), 1.2f).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f)
        })
    }

    override fun sprites() = listOf(
        // Le rotor : trois pales effilées et le moyeu.
        Sprite(ROTOR, RectF(HX - 96f, HY - 96f, HX + 96f, HY + 96f)) { b ->
            for (k in 0 until 3) {
                b.c.save(); b.c.rotate(k * 120f, HX, HY)
                val blade = Path().apply {
                    moveTo(HX - 4f, HY); quadTo(HX - 9f, HY - 30f, HX - 3f, HY - 92f); lineTo(HX + 1f, HY - 92f)
                    quadTo(HX + 6f, HY - 40f, HX + 4f, HY); close()
                }
                b.body(blade, 0xFFF4F4F0.toInt(), .2f, 80f, outline = .8f, washAlpha = 255)
                b.wash(rect(HX - 3f, HY - 92f, HX + 1f, HY - 84f), Ink.RUBRIC, 220)
                b.c.restore()
            }
            b.body(ellipse(HX, HY, 8f, 8f), 0xFFE8E8E4.toInt(), .25f, 30f, outline = .8f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val ring = RectF(HX - 22f, HY - 22f, HX + 22f, HY + 22f)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les vagues filent, les éoliennes du fond tournent.
        line.color = withAlpha(Ink.WHITE, 110); line.strokeWidth = 1.2f
        for (r in 0 until 5) {
            val y = HORIZON + 14f + r * 22f
            for (k in 0 until 8) {
                val x = 40f + ((k * 37f + r * 13f + t * (6f + r * 3f)) % 290f)
                c.drawLine(x, y + 2f * sin(t + k), x + 8f + r * 2f, y + 2f * sin(t + k), line)
            }
        }
        line.color = 0xFFF0F0EC.toInt(); line.strokeWidth = 1.1f
        for (k in FAR_X.indices) {
            val x = FAR_X[k]; val y = HORIZON - FAR_H[k]; val len = FAR_H[k] * .45f
            for (j in 0 until 3) {
                val a = t * (1.6f + .2f * k) + k + j * 2.0943952f
                c.drawLine(x, y, x + len * sin(a), y - len * cos(a), line)
            }
        }

        // L'anneau d'aimants tourne avec le rotor, nord et sud alternés.
        val deg = t * SPIN
        for (k in 0 until MAGNETS) {
            arc.color = if (k % 2 == 0) 0xFFC83A2A.toInt() else 0xFF3A6AC8.toInt()
            c.drawArc(ring, deg + k * 360f / MAGNETS + 1.5f, 360f / MAGNETS - 3f, false, arc)
        }
        // Les bobines s'allument quand un aimant passe devant.
        for (k in 0 until COILS) {
            val a = k * 6.2831855f / COILS
            val glow = .5f + .5f * cos((deg * DEG - a) * MAGNETS)
            fill.color = withAlpha(0xFFFFD040.toInt(), (200 * glow).toInt())
            c.drawCircle(HX + 30f * cos(a), HY + 30f * sin(a), 2.2f, fill)
        }
        s.draw(c, ROTOR, deg = deg, px = HX, py = HY)
        // Le courant descend le mât jusqu'au câble sous la mer.
        for (k in 0 until 4) {
            val f = (t * .5f + k / 4f) % 1f
            fill.color = withAlpha(0xFFFFE070.toInt(), (220 * sin(f * 3.1415927f)).toInt())
            c.drawCircle(HX, HY + 40f + (300f - HY - 40f) * f, 2f, fill)
        }
        // Deux mouettes passent.
        line.color = 0xFF2A2A2E.toInt(); line.strokeWidth = 1.2f
        for (k in 0 until 2) {
            val x = 30f + ((t * (14f + 5f * k) + k * 150f) % 320f)
            val y = 140f + 30f * k + 6f * sin(t * .7f + k)
            val flap = 4f * sin(t * 6f + k)
            c.drawLine(x - 7f, y - flap, x, y, line); c.drawLine(x, y, x + 7f, y - flap, line)
        }
    }

    private companion object {
        const val HORIZON = 214f
        const val HX = 184f
        const val HY = 136f
        const val SPIN = 40f
        const val MAGNETS = 16
        const val COILS = 18
        const val ROTOR = 1
        const val DEG = .017453292f
        val FAR_X = floatArrayOf(70f, 112f, 286f)
        val FAR_H = floatArrayOf(52f, 40f, 60f)
    }
}
