package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Plutonium — le robot sur Mars. La chaleur d'un bloc de plutonium 238 fait l'électricité des
 * robots martiens et des sondes lointaines, là où le soleil ne suffit pas. Le robot roule sur
 * Mars ; ses roues tournent, le sol défile, sa caméra regarde autour d'elle, et la pile au
 * plutonium, à l'arrière, rayonne sa chaleur.
 */
internal class ScenePlutonium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_mars_rover
    override val noteRes = R.string.card_note_mars_rover
    override val explainRes = R.string.card_explain_mars_rover

    override fun engrave(b: Burin) {
        // Le ciel couleur caramel de Mars, un petit soleil pâle.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, HORIZON,
            0xFFC8A078.toInt(), 0xFFE8C8A8.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, HORIZON, b.p)
        b.glow(92f, 96f, 26f, 0xFFF4F0E8.toInt(), 200)
        b.c.drawCircle(92f, 96f, 6f, b.pen(0xFFFBF8F0.toInt()))
        // Les buttes au loin, puis la plaine rouille.
        b.body(Path().apply {
            moveTo(40f, HORIZON); lineTo(60f, 170f); lineTo(110f, 168f); lineTo(124f, HORIZON - 4f); lineTo(200f, HORIZON - 2f)
            lineTo(222f, 160f); lineTo(270f, 158f); lineTo(292f, HORIZON); lineTo(320f, HORIZON); lineTo(320f, HORIZON + 2f); lineTo(40f, HORIZON + 2f); close()
        }, 0xFFB07A5A.toInt(), .4f, 80f, outline = .7f, washAlpha = 255)
        val plain = rect(40f, HORIZON, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, HORIZON, 0f, 340f,
            0xFFC07A4A.toInt(), 0xFF8A4A2A.toInt(), Shader.TileMode.CLAMP)
        b.c.drawPath(plain, b.p)
        b.stipple(plain, 700, .6f, withAlpha(0xFF4A2A1A.toInt(), 150), 9)
        b.hatchRect(RectF(40f, HORIZON, 320f, 340f), 0f, 4.4f, .45f, withAlpha(0xFF5A2A1A.toInt(), 90))
    }

    override fun sprites() = listOf(
        Sprite(BODY, RectF(96f, 120f, 270f, 250f)) { b ->
            // La pile au plutonium : un cylindre à ailettes, penché à l'arrière.
            b.body(limb(130f, 206f, 110f, 222f, 16f, 16f), 0xFF5A5E66.toInt(), .4f, 30f, outline = .8f, washAlpha = 255)
            for (k in 0 until 6) b.line(128f - k * 3.4f, 196f + k * 2.7f, 116f - k * 3.4f, 210f + k * 2.7f, 1f, 0xFF2A2C30.toInt())
            // Les bras de suspension vers les roues.
            for ((x0, x1) in listOf(150f to 140f, 186f to 184f, 220f to 228f)) b.line(x0, 214f, x1, WY, 2.4f, 0xFFB8BCC4.toInt())
            b.line(140f, 230f, 228f, 230f, 2f, 0xFFB8BCC4.toInt())
            // Le châssis blanc et son pont.
            b.body(rect(130f, 196f, 238f, 216f), 0xFFE8E4DC.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
            b.body(rect(140f, 188f, 226f, 197f), 0xFFC8C0B0.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
            b.wash(rect(130f, 196f, 238f, 200f), Ink.WHITE, 120)
            // Le bras robotique replié devant.
            b.body(limb(238f, 206f, 258f, 196f, 4f, 4f), 0xFFB8BCC4.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
            b.body(limb(258f, 196f, 262f, 214f, 4f, 3.5f), 0xFFB8BCC4.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
            b.body(ellipse(262f, 218f, 5f, 5f), 0xFF5A5E66.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
            // Le mât (la tête est à part : elle tourne).
            b.body(rect(MAST_X - 2.5f, 142f, MAST_X + 2.5f, 190f), 0xFFE8E4DC.toInt(), .2f, 90f, outline = .7f, washAlpha = 255)
            b.body(rect(196f, 180f, 210f, 188f), 0xFF8A8E94.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        },
        Sprite(WHEEL, RectF(WX0 - 13f, WY - 13f, WX0 + 13f, WY + 13f)) { b ->
            b.body(ellipse(WX0, WY, 11f, 11f), 0xFF8A8E94.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
            for (k in 0 until 12) {
                val a = k * 30f * .017453292f
                b.line(WX0 + 7f * kotlin.math.cos(a), WY + 7f * sin(a), WX0 + 11f * kotlin.math.cos(a), WY + 11f * sin(a), 1.4f, 0xFF3A3C40.toInt())
            }
            b.body(ellipse(WX0, WY, 4f, 4f), 0xFFC8CCD2.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
            b.line(WX0, WY - 4f, WX0, WY + 4f, .8f)
        },
        Sprite(HEAD, RectF(MAST_X - 16f, 128f, MAST_X + 16f, 148f)) { b ->
            b.body(rect(MAST_X - 13f, 132f, MAST_X + 13f, 144f), 0xFFE8E4DC.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            for (x in floatArrayOf(MAST_X + 5f, MAST_X + 10f)) b.body(ellipse(x, 138f, 2.4f, 2.4f), 0xFF1A1A1E.toInt(), 0f, outline = .4f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val run = t * SPEED
        // Un tourbillon de poussière glisse au loin.
        val dx = 340f - ((t * 4f) % 380f)
        for (k in 0 until 6) {
            fill.color = withAlpha(0xFFD8B090.toInt(), 40 - k * 5)
            c.drawOval(dx - 4f - k * 2f + 3f * sin(t * 3f + k), HORIZON - 8f - k * 9f, dx + 4f + k * 2f + 3f * sin(t * 3f + k), HORIZON - k * 9f, fill)
        }
        // Les traces des roues et les cailloux défilent vers l'arrière.
        line.color = withAlpha(0xFF5A2A1A.toInt(), 120); line.strokeWidth = 1.4f
        var x = 40f - (run % 8f)
        while (x < 140f) { c.drawLine(x, WY + 11f, x + 4f, WY + 11f, line); c.drawLine(x, WY + 15f, x + 4f, WY + 15f, line); x += 8f }
        rocks(c, run, false)
        // Le robot : châssis, roues qui tournent, tête qui regarde autour d'elle.
        val bump = .8f * sin(t * 2.3f)
        s.draw(c, BODY, 0f, bump)
        val spin = run / 11f * 57.29578f
        for (k in 0 until 3) s.draw(c, WHEEL, k * 44f, 0f, spin, WX0, WY)
        s.draw(c, HEAD, 0f, bump, 10f * sin(t * .5f), MAST_X, 142f)
        rocks(c, run, true)
        // La chaleur de la pile au plutonium tremble au-dessus des ailettes.
        line.strokeWidth = 1f
        for (k in 0 until 3) {
            val f = (t * .6f + k / 3f) % 1f
            line.color = withAlpha(0xFFFF8A5A.toInt(), (110 * sin(f * 3.1415927f)).toInt())
            val y = 196f - 30f * f + bump
            c.drawLine(108f + k * 8f + 3f * sin(t * 4f + k), y, 112f + k * 8f + 3f * sin(t * 4f + k + 1f), y - 6f, line)
        }
        fill.color = withAlpha(0xFFFF5A2A.toInt(), (40 + 20 * sin(t * 1.7f)).toInt())
        c.drawCircle(120f, 214f + bump, 16f, fill)
    }

    /** Les cailloux, plus gros et plus rapides au premier plan ; ceux de devant passent devant les roues. */
    private fun rocks(c: Canvas, run: Float, front: Boolean) {
        for (k in 0 until ROCKS) {
            val depth = .4f + .6f * hash(k, 1)
            val ry = HORIZON + 10f + 120f * depth * depth + 10f * hash(k, 3)
            if ((ry > WY + 8f) != front) continue
            val rx = 40f + (((hash(k, 2) * 320f - run * depth) % 320f) + 320f) % 320f
            val r = 2f + 7f * depth * hash(k, 4)
            fill.color = 0xFF5A3020.toInt(); c.drawOval(rx - r, ry - r * .6f, rx + r, ry + r * .6f, fill)
            fill.color = withAlpha(0xFFE8B088.toInt(), 160); c.drawOval(rx - r * .6f, ry - r * .55f, rx + r * .2f, ry - r * .1f, fill)
        }
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val HORIZON = 180f
        const val SPEED = 12f
        const val WX0 = 140f
        const val WY = 244f
        const val MAST_X = 214f
        const val ROCKS = 22
        const val BODY = 1
        const val WHEEL = 2
        const val HEAD = 3
    }
}
