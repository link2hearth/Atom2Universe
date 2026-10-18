package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Europium — le billet sous la lampe UV. Les billets en euros cachent des encres et des fibres
 * qui ne s'allument que sous les ultraviolets ; le rouge vient de l'europium. Une lampe UV passe
 * au-dessus d'un billet posé sur le comptoir : sous sa tache violette, les fibres rouges, vertes
 * et bleues et le cercle d'étoiles s'allument.
 */
internal class SceneEuropium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_uv_banknote
    override val noteRes = R.string.card_note_uv_banknote
    override val explainRes = R.string.card_explain_uv_banknote

    override fun engrave(b: Burin) {
        // Le comptoir, dans la pénombre.
        b.body(rect(40f, 50f, 320f, 340f), 0xFF4A3424.toInt(), .45f, 8f, outline = 0f, washAlpha = 255)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 2f, 7f, .8f, withAlpha(0xFF1A100A.toInt(), 120))
        // Le billet : papier bleuté, une arche, un cercle d'étoiles, des chiffres sans texte.
        b.wash(rect(NL + 4f, NT + 5f, NR + 4f, NB + 5f), 0xFF000000.toInt(), 90)
        val note = rect(NL, NT, NR, NB)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(NL, 0f, NR, 0f,
            intArrayOf(0xFFA8C0D8.toInt(), 0xFFC8D8E8.toInt(), 0xFF98B4D0.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(note, b.p)
        b.hatch(note, 70f, 3f, .4f, withAlpha(0xFF3A5A80.toInt(), 120))
        b.stroke(note, .9f)
        // L'arche : deux piliers et un cintre.
        val arch = Path().apply {
            moveTo(96f, 268f); lineTo(96f, 210f); quadTo(96f, 182f, 126f, 182f); quadTo(156f, 182f, 156f, 210f); lineTo(156f, 268f)
            lineTo(144f, 268f); lineTo(144f, 212f); quadTo(144f, 194f, 126f, 194f); quadTo(108f, 194f, 108f, 212f); lineTo(108f, 268f); close()
        }
        b.body(arch, 0xFF5A7AA8.toInt(), .45f, 30f, outline = .8f, washAlpha = 220)
        for (k in 0 until 12) {
            val a = k * 30f * DEG
            star(b, STAR_X + 22f * cos(a), STAR_Y + 22f * sin(a), 3.2f, 0xFFE8D060.toInt(), 200)
        }
        b.body(rect(242f, 252f, 290f, 274f), 0xFF5A7AA8.toInt(), .3f, 0f, outline = .7f, washAlpha = 180)
        b.goldStroke(rect(NL + 6f, NT + 6f, NR - 6f, NB - 6f), 1f)
        // La lampe UV posée sur son rail, tout en haut (la tête bouge dans l'animation).
        b.body(rect(40f, 66f, 320f, 74f), 0xFF6A6E74.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
    }

    private fun star(b: Burin, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        val p = Path()
        for (i in 0 until 10) {
            val a = (-90f + i * 36f) * DEG
            val rr = if (i % 2 == 0) r else r * .45f
            if (i == 0) p.moveTo(x + rr * cos(a), y + rr * sin(a)) else p.lineTo(x + rr * cos(a), y + rr * sin(a))
        }
        p.close()
        b.wash(p, color, alpha)
    }

    override fun sprites() = listOf(
        // Ce qui ne se voit que sous les ultraviolets.
        Sprite(GLOW, RectF(NL, NT, NR, NB)) { b ->
            val rnd = kotlin.random.Random(11)
            for (k in 0 until 70) {
                val x = NL + 8f + rnd.nextFloat() * (NR - NL - 16f)
                val y = NT + 8f + rnd.nextFloat() * (NB - NT - 16f)
                val a = rnd.nextFloat() * 6.28f
                val len = 5f + rnd.nextFloat() * 7f
                val color = FIBRES[k % 3]
                b.c.drawLine(x, y, x + len * cos(a), y + len * sin(a), b.pen(color, 1.3f))
            }
            for (k in 0 until 12) {
                val a = k * 30f * DEG
                star(b, STAR_X + 22f * cos(a), STAR_Y + 22f * sin(a), 4.2f, 0xFFFF5A3A.toInt(), 255)
            }
            b.wash(ellipse(STAR_X, STAR_Y, 14f, 14f), 0xFF5AFF8A.toInt(), 110)
            b.wash(Path().apply { addRoundRect(96f, 182f, 156f, 268f, 30f, 30f, Path.Direction.CW) }, 0xFF5AA8FF.toInt(), 70)
            b.c.drawRect(242f, 252f, 290f, 274f, b.pen(0xFFFF7A3A.toInt(), 1.6f))
        },
        // La tête de la lampe et son tube violet.
        Sprite(LAMP, RectF(LAMP_X - 34f, 60f, LAMP_X + 34f, 104f)) { b ->
            b.body(Path().apply { addRoundRect(LAMP_X - 30f, 72f, LAMP_X + 30f, 96f, 6f, 6f, Path.Direction.CW) }, 0xFF2A2A30.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.wash(Path().apply { addRoundRect(LAMP_X - 26f, 92f, LAMP_X + 26f, 100f, 4f, 4f, Path.Direction.CW) }, 0xFFB070FF.toInt(), 255)
            b.wash(rect(LAMP_X - 22f, 94f, LAMP_X + 22f, 96f), 0xFFF0E0FF.toInt(), 230)
            b.body(rect(LAMP_X - 6f, 64f, LAMP_X + 6f, 74f), 0xFF6A6E74.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beam = Path()
    private val spot = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La lampe glisse sur son rail, d'un bout du billet à l'autre.
        val dx = 92f * sin(t * .42f)
        val x = LAMP_X + dx
        beam.reset()
        beam.moveTo(x - 24f, 100f); beam.lineTo(x + 24f, 100f); beam.lineTo(x + 58f, 286f); beam.lineTo(x - 58f, 286f); beam.close()
        fill.color = withAlpha(0xFF9A5AFF.toInt(), 36); c.drawPath(beam, fill)
        spot.reset()
        spot.addOval(x - 56f, NT - 4f, x + 56f, NB + 4f, Path.Direction.CW)
        // Sous la tache : le papier s'éteint en violet, les fibres et les étoiles s'allument.
        c.save(); c.clipPath(spot)
        fill.color = withAlpha(0xFF2A1060.toInt(), 120); c.drawRect(NL, NT, NR, NB, fill)
        s.draw(c, GLOW, alpha = (225 + 30 * sin(t * 3.1f)).toInt())
        c.restore()
        fill.color = withAlpha(0xFFB070FF.toInt(), 40); c.drawOval(x - 56f, NT - 4f, x + 56f, NB + 4f, fill)
        s.draw(c, LAMP, dx)
    }

    private companion object {
        const val NL = 70f
        const val NT = 166f
        const val NR = 296f
        const val NB = 284f
        const val STAR_X = 214f
        const val STAR_Y = 212f
        const val LAMP_X = 180f
        const val GLOW = 1
        const val LAMP = 2
        const val DEG = .017453292f
        val FIBRES = intArrayOf(0xFFFF3A2A.toInt(), 0xFF3AFF6A.toInt(), 0xFF3A8AFF.toInt())
    }
}
