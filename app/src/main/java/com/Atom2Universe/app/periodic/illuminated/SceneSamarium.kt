package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Samarium — le micro de guitare. Les aimants au samarium-cobalt tiennent sous la chaleur : on en
 * met dans les micros de guitare électrique. Le médiator gratte les six cordes vers le bas puis
 * vers le haut ; chaque corde vibre au-dessus des plots aimantés du micro, et l'ampli derrière
 * renvoie le son en ondes.
 */
internal class SceneSamarium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_guitar_pickup
    override val noteRes = R.string.card_note_guitar_pickup
    override val explainRes = R.string.card_explain_guitar_pickup

    override fun engrave(b: Burin) {
        // L'ampli derrière : simili-cuir noir, toile tissée, le haut-parleur deviné.
        b.wash(rect(40f, 50f, 320f, 340f), 0xFF14100C.toInt(), 255)
        b.body(rect(40f, 50f, 320f, 130f), 0xFF1E1C1A.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        val grille = rect(60f, 62f, 300f, 122f)
        b.wash(grille, 0xFF6A5A40.toInt(), 255)
        b.hatch(grille, 45f, 2.2f, .45f, withAlpha(0xFF2A2010.toInt(), 200))
        b.hatch(grille, -45f, 2.2f, .45f, withAlpha(0xFF2A2010.toInt(), 200))
        b.c.drawCircle(SPK_X, SPK_Y, 26f, b.pen(withAlpha(0xFF000000.toInt(), 90), 3f))
        b.stroke(grille, 1.2f, 0xFFB0A080.toInt())
        // Le corps de la guitare, ombré du centre vers les bords.
        val body = Path().apply {
            moveTo(40f, 152f); quadTo(120f, 126f, 200f, 142f); quadTo(290f, 128f, 320f, 152f)
            lineTo(320f, 340f); lineTo(40f, 340f); close()
        }
        b.pen(0xFF000000.toInt()).shader = RadialGradient(190f, 250f, 190f,
            intArrayOf(0xFFF0B040.toInt(), 0xFFB04A10.toInt(), 0xFF2A1408.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(body, b.p)
        b.stroke(body, 1.2f)
        // La plaque de protection, le micro et ses six plots aimantés.
        val guard = Path().apply { addRoundRect(70f, 150f, 252f, 300f, 26f, 26f, Path.Direction.CW) }
        b.body(guard, 0xFFF2EEE4.toInt(), .15f, 30f, outline = .8f, washAlpha = 255)
        val pickup = Path().apply { addRoundRect(176f, 158f, 224f, 224f, 5f, 5f, Path.Direction.CW) }
        b.body(pickup, 0xFFEFE2C2.toInt(), .2f, 90f, outline = 1f, washAlpha = 255)
        for (i in 0 until 6) {
            b.pen(0xFF000000.toInt()).shader = RadialGradient(198f, STR_Y[i] - 1.5f, 5f,
                intArrayOf(0xFFF0F2F4.toInt(), 0xFF6A6E74.toInt()), null, Shader.TileMode.CLAMP)
            b.c.drawCircle(200f, STR_Y[i], 3.4f, b.p)
            b.c.drawCircle(200f, STR_Y[i], 3.4f, b.pen(Ink.SEPIA, .6f))
        }
        for (y in floatArrayOf(162f, 220f)) b.body(ellipse(200f, y, 1.8f, 1.8f), 0xFFC8CCD2.toInt(), 0f, outline = .5f, washAlpha = 255)
        // Le chevalet chromé, un pontet par corde.
        b.body(Path().apply { addRoundRect(270f, 154f, 302f, 228f, 4f, 4f, Path.Direction.CW) }, 0xFFC8CCD2.toInt(), .3f, 90f, outline = .9f, washAlpha = 255)
        for (i in 0 until 6) b.body(rect(BRIDGE - 2f, STR_Y[i] - 3f, BRIDGE + 6f, STR_Y[i] + 3f), 0xFFE8EAEE.toInt(), 0f, outline = .5f, washAlpha = 255)
        // Les boutons de volume et de tonalité.
        for ((kx, ky) in listOf(252f to 262f, 284f to 288f)) {
            b.body(ellipse(kx, ky, 10f, 10f), 0xFF1A1A1E.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.body(ellipse(kx, ky, 5f, 5f), 0xFFC8CCD2.toInt(), 0f, outline = .5f, washAlpha = 255)
        }
    }

    override fun sprites() = listOf(
        // Le médiator, en écaille rouge.
        Sprite(PICK, RectF(PICK_X - 12f, PICK_Y - 12f, PICK_X + 12f, PICK_Y + 12f)) { b ->
            val pick = Path().apply {
                moveTo(PICK_X - 10f, PICK_Y - 8f); quadTo(PICK_X, PICK_Y - 13f, PICK_X + 10f, PICK_Y - 8f)
                quadTo(PICK_X + 8f, PICK_Y + 2f, PICK_X, PICK_Y + 10f); quadTo(PICK_X - 8f, PICK_Y + 2f, PICK_X - 10f, PICK_Y - 8f); close()
            }
            b.body(pick, 0xFFC8301E.toInt(), .2f, 40f, outline = .8f, washAlpha = 255)
            b.stipple(pick, 30, .8f, withAlpha(0xFF5A1008.toInt(), 160), 3)
            b.wash(ellipse(PICK_X - 3f, PICK_Y - 6f, 4f, 2f), Ink.WHITE, 120)
        }
    )

    // ───────────── animation ─────────────

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val string = Path()
    private val blur = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val cycle = (t / STRUM).toInt()
        val tc = t - cycle * STRUM
        val start = cycle * STRUM
        var total = 0f
        for (i in 0 until 6) {
            // Le dernier coup de médiator reçu par cette corde, vers le bas ou vers le haut.
            val down = start + DOWN * (STR_Y[i] - TOP) / (LOW - TOP)
            val up = start + UP + DOWN * (LOW - STR_Y[i]) / (LOW - TOP)
            val hit = if (t >= up) up else if (t >= down) down else up - STRUM
            val amp = (3.2f + .5f * i) * exp(-(t - hit) * 1.3f)
            total += amp
            val y0 = STR_Y[i]
            val swing = cos(t * OMEGA + i * 1.7f)
            string.reset(); blur.reset()
            var x = 40f
            while (x <= BRIDGE + .1f) {
                val shape = sin(PI * (x - NUT) / (BRIDGE - NUT))
                val dy = amp * shape
                if (x == 40f) { string.moveTo(x, y0 + dy * swing); blur.moveTo(x, y0 - dy) } else { string.lineTo(x, y0 + dy * swing); blur.lineTo(x, y0 - dy) }
                x += 12.3f
            }
            x -= 12.3f
            while (x >= 40f - .1f) {
                blur.lineTo(x, y0 + amp * sin(PI * (x - NUT) / (BRIDGE - NUT)))
                x -= 12.3f
            }
            blur.close()
            band.color = withAlpha(0xFFE8EAEE.toInt(), min(70, (amp * 14).toInt()))
            c.drawPath(blur, band)
            line.color = withAlpha(0xFF000000.toInt(), 90); line.strokeWidth = WIDTH[i]
            c.save(); c.translate(1.5f, 2.5f); c.drawPath(string, line); c.restore()
            line.color = 0xFFE4E6EA.toInt(); c.drawPath(string, line)
        }
        // Le médiator monte et descend à travers les cordes.
        val py = when {
            tc < DOWN -> TOP + (LOW - TOP) * tc / DOWN
            tc < UP -> LOW
            tc < UP + DOWN -> LOW - (LOW - TOP) * (tc - UP) / DOWN
            else -> TOP
        }
        s.draw(c, PICK, 2f * sin(t * 1.1f), py - PICK_Y, -10f + 20f * (py - TOP) / (LOW - TOP), PICK_X, PICK_Y)
        // L'ampli renvoie le son.
        val loud = (total / 22f).coerceIn(0f, 1f)
        if (loud > .02f) {
            line.strokeWidth = 1.4f
            for (k in 0 until 3) {
                val f = ((t * .9f + k / 3f) % 1f)
                line.color = withAlpha(0xFFF0D8A0.toInt(), (150 * loud * (1f - f)).toInt())
                c.drawCircle(SPK_X, SPK_Y, 28f + 34f * f, line)
            }
        }
    }

    private companion object {
        const val STRUM = 2.6f
        const val DOWN = .18f
        const val UP = 1.3f
        const val TOP = 146f
        const val LOW = 232f
        const val NUT = -160f
        const val BRIDGE = 286f
        const val OMEGA = 57f
        const val PI = 3.1415927f
        const val PICK_X = 122f
        const val PICK_Y = 150f
        const val SPK_X = 180f
        const val SPK_Y = 92f
        const val PICK = 1
        val STR_Y = floatArrayOf(166f, 176f, 186f, 196f, 206f, 216f)
        val WIDTH = floatArrayOf(.7f, .85f, 1f, 1.25f, 1.5f, 1.8f)
    }
}
