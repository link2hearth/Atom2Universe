package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iridium — la bougie d'allumage. L'électrode des meilleures bougies est en iridium : l'étincelle
 * l'use à peine en des milliers de kilomètres. Un cylindre de moteur en coupe : la soupape
 * d'admission laisse entrer le mélange, le piston le comprime, la bougie claque, la flamme
 * repousse le piston, puis la soupape d'échappement laisse sortir les gaz brûlés.
 */
internal class SceneIridium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_spark_plug
    override val noteRes = R.string.card_note_spark_plug
    override val explainRes = R.string.card_explain_spark_plug

    override fun engrave(b: Burin) {
        // Le bloc moteur coupé : fonte grise hachurée.
        b.body(rect(40f, 50f, 320f, 340f), 0xFF8A8E94.toInt(), .5f, 45f, outline = 0f, washAlpha = 255)
        // L'alésage du cylindre, sombre, et le carter en bas.
        b.wash(rect(BORE_L, HEAD, BORE_R, 250f), 0xFF1A1C20.toInt(), 255)
        b.wash(rect(84f, 250f, 276f, 340f), 0xFF14161A.toInt(), 255)
        b.line(BORE_L, HEAD, BORE_L, 250f, 1.4f, 0xFFC8CCD2.toInt()); b.line(BORE_R, HEAD, BORE_R, 250f, 1.4f, 0xFFC8CCD2.toInt())
        // Les conduits d'admission (à gauche) et d'échappement (à droite).
        b.wash(Path().apply { moveTo(40f, 74f); lineTo(80f, 74f); quadTo(140f, 76f, 150f, 112f); lineTo(140f, HEAD); lineTo(126f, HEAD); quadTo(116f, 96f, 76f, 96f); lineTo(40f, 96f); close() }, 0xFF20242A.toInt(), 255)
        b.wash(Path().apply { moveTo(320f, 74f); lineTo(280f, 74f); quadTo(220f, 76f, 210f, 112f); lineTo(220f, HEAD); lineTo(234f, HEAD); quadTo(244f, 96f, 284f, 96f); lineTo(320f, 96f); close() }, 0xFF20242A.toInt(), 255)
        // La bougie, vissée au milieu de la culasse : borne, isolant blanc, écrou.
        b.body(rect(PLUG_X - 3f, 50f, PLUG_X + 3f, 64f), 0xFFC8CCD2.toInt(), .2f, 90f, outline = .6f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(PLUG_X - 8f, 62f, PLUG_X + 8f, 92f, 4f, 4f, Path.Direction.CW) }, 0xFFF4F2EA.toInt(), .15f, 90f, outline = .8f, washAlpha = 255)
        for (k in 0 until 4) b.line(PLUG_X - 8f, 68f + k * 5f, PLUG_X + 8f, 68f + k * 5f, .5f, 0xFFB8B4A8.toInt())
        b.body(rect(PLUG_X - 12f, 92f, PLUG_X + 12f, 104f), 0xFFB8BEC6.toInt(), .3f, 90f, outline = .8f, washAlpha = 255)
        b.body(rect(PLUG_X - 7f, 104f, PLUG_X + 7f, HEAD), 0xFF9AA0A8.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        for (k in 0 until 3) b.line(PLUG_X - 7f, 107f + k * 4f, PLUG_X + 7f, 109f + k * 4f, .6f)
        // Les électrodes : le fil fin d'iridium au centre, le crochet de masse.
        b.line(PLUG_X, HEAD, PLUG_X, HEAD + 5f, 1.2f, 0xFFE8EAEE.toInt())
        b.c.drawPath(Path().apply { moveTo(PLUG_X + 6f, HEAD); lineTo(PLUG_X + 6f, HEAD + 9f); lineTo(PLUG_X - 1f, HEAD + 9f) }, b.pen(0xFFB8BEC6.toInt(), 1.8f))
    }

    override fun sprites() = listOf(
        Sprite(PISTON, RectF(BORE_L + 1f, PISTON_TOP - 2f, BORE_R - 1f, PISTON_TOP + 40f)) { b ->
            val body = Path().apply { addRoundRect(BORE_L + 2f, PISTON_TOP, BORE_R - 2f, PISTON_TOP + 38f, 3f, 3f, Path.Direction.CW) }
            b.pen(0xFF000000.toInt()).shader = LinearGradient(BORE_L, 0f, BORE_R, 0f,
                intArrayOf(0xFF8A8E94.toInt(), 0xFFE8EAEE.toInt(), 0xFF6A6E74.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            b.c.drawPath(body, b.p)
            b.stroke(body, .9f)
            for (k in 0 until 3) b.line(BORE_L + 2f, PISTON_TOP + 6f + k * 5f, BORE_R - 2f, PISTON_TOP + 6f + k * 5f, 1f, 0xFF3A3E46.toInt())
            b.body(ellipse(PLUG_X, PIN_Y0, 6f, 6f), 0xFF5A5E66.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        },
        Sprite(INTAKE, RectF(VALVE_L - 13f, 60f, VALVE_L + 13f, HEAD + 4f)) { b -> valve(b, VALVE_L) },
        Sprite(EXHAUST, RectF(VALVE_R - 13f, 60f, VALVE_R + 13f, HEAD + 4f)) { b -> valve(b, VALVE_R) },
        Sprite(CRANK, RectF(PLUG_X - 52f, CRANK_Y - 52f, PLUG_X + 52f, CRANK_Y + 52f)) { b ->
            b.body(Path().apply { addArc(RectF(PLUG_X - 48f, CRANK_Y - 48f, PLUG_X + 48f, CRANK_Y + 48f), 20f, 140f); lineTo(PLUG_X, CRANK_Y); close() }, 0xFF5A5E66.toInt(), .4f, 30f, outline = .8f, washAlpha = 255)
            b.body(ellipse(PLUG_X, CRANK_Y, 14f, 14f), 0xFF8A8E94.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.body(ellipse(PLUG_X, CRANK_Y - CRANK_R, 9f, 9f), 0xFFB8BEC6.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        }
    )

    private fun valve(b: Burin, x: Float) {
        b.body(rect(x - 2f, 62f, x + 2f, HEAD - 3f), 0xFFB8BEC6.toInt(), .2f, 90f, outline = .6f, washAlpha = 255)
        b.body(poly(x - 11f, HEAD, x + 11f, HEAD, x + 3f, HEAD - 6f, x - 3f, HEAD - 6f), 0xFFC8CCD2.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(rect(x - 6f, 62f, x + 6f, 66f), 0xFF8A8E94.toInt(), .2f, 0f, outline = .5f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rod = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF9AA0A8.toInt() }
    private val flame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(PLUG_X, HEAD + 8f, 80f, intArrayOf(0xFFFFF0B0.toInt(), 0xFFFF8A20.toInt(), 0x00FF5010),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Quatre temps sur deux tours de vilebrequin.
        val deg = (t % CYCLE) / CYCLE * 720f
        val a = deg * DEG
        val pinY = CRANK_Y - (CRANK_R * cos(a) + sqrt(ROD * ROD - CRANK_R * CRANK_R * sin(a) * sin(a)))
        val top = PISTON_TOP + (pinY - PIN_Y0)

        // Ce qui remplit la chambre : mélange bleuté, flamme, puis gaz brûlés.
        val gas = when {
            deg < 180f -> withAlpha(0xFF6AA8E0.toInt(), (60 * deg / 180f).toInt())
            deg < 360f -> withAlpha(0xFF6AA8E0.toInt(), 60 + (50 * (deg - 180f) / 180f).toInt())
            deg < 540f -> withAlpha(0xFF8A6A50.toInt(), (150 * (1f - (deg - 360f) / 180f) + 60).toInt())
            else -> withAlpha(0xFF6A6A6A.toInt(), (60 * (1f - (deg - 540f) / 180f)).toInt())
        }
        fill.color = gas
        c.drawRect(BORE_L, HEAD, BORE_R, top, fill)
        if (deg in 360f..470f) {
            val k = (deg - 360f) / 110f
            c.save(); c.clipRect(BORE_L, HEAD, BORE_R, top)
            flame.alpha = (255 * (1f - k * k)).toInt()
            c.drawCircle(PLUG_X, HEAD + 8f, 10f + 90f * k, flame)
            c.restore()
        }
        // L'étincelle entre les électrodes.
        val spark = 1f - kotlin.math.abs(deg - 360f) / 10f
        if (spark > 0f) {
            fill.color = withAlpha(0xFFE8F4FF.toInt(), (255 * spark).toInt()); c.drawCircle(PLUG_X + 1f, HEAD + 7f, 3f + 5f * spark, fill)
            rod.color = 0xFFFFFFFF.toInt(); rod.strokeWidth = 1.2f
            c.drawLine(PLUG_X, HEAD + 5f, PLUG_X + 2f, HEAD + 9f, rod)
        }

        // Les soupapes s'ouvrent chacune à son temps.
        val inLift = if (deg < 180f) 9f * sin(deg * DEG) else 0f
        val exLift = if (deg > 540f) 9f * sin((deg - 540f) * DEG) else 0f
        s.draw(c, INTAKE, dy = inLift)
        s.draw(c, EXHAUST, dy = exLift)
        // Le mélange entre, les gaz brûlés sortent.
        for (k in 0 until 6) {
            val f = (t * 2f + k / 6f) % 1f
            if (inLift > 1f) { fill.color = withAlpha(0xFF8AC8FF.toInt(), (40 * inLift * sin(f * 3.1415927f)).toInt()); c.drawCircle(60f + 80f * f, 86f + 20f * f * f, 3f, fill) }
            if (exLift > 1f) { fill.color = withAlpha(0xFF9A9A9A.toInt(), (40 * exLift * sin(f * 3.1415927f)).toInt()); c.drawCircle(220f + 80f * f, 110f - 26f * f, 3.5f + 3f * f, fill) }
        }

        // Le vilebrequin, la bielle et le piston.
        s.draw(c, CRANK, deg = deg, px = PLUG_X, py = CRANK_Y)
        val cx = PLUG_X + CRANK_R * sin(a); val cy = CRANK_Y - CRANK_R * cos(a)
        rod.color = 0xFF9AA0A8.toInt(); rod.strokeWidth = 10f
        c.drawLine(cx, cy, PLUG_X, pinY, rod)
        rod.color = 0xFF3A3E46.toInt(); rod.strokeWidth = 1f
        c.drawLine(cx, cy, PLUG_X, pinY, rod)
        s.draw(c, PISTON, dy = top - PISTON_TOP)
    }

    private companion object {
        const val CYCLE = 3.2f
        const val HEAD = 122f
        const val BORE_L = 130f
        const val BORE_R = 230f
        const val PLUG_X = 180f
        const val VALVE_L = 148f
        const val VALVE_R = 212f
        const val CRANK_Y = 300f
        const val CRANK_R = 40f
        const val ROD = 110f
        const val PIN_Y0 = 150f
        const val PISTON_TOP = 132f
        const val PISTON = 1
        const val INTAKE = 2
        const val EXHAUST = 3
        const val CRANK = 4
        const val DEG = .017453292f
    }
}
