package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tungstène — le filament de l'ampoule. Le tungstène fond plus tard que tous les métaux : chauffé
 * à près de 3 000 °C, son fil enroulé brille sans fondre. L'ampoule en gros plan : le courant
 * monte, le filament rougit, passe à l'orange, au jaune puis au blanc, éclaire la pièce, puis
 * redescend et s'éteint.
 */
internal class SceneTungsten : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_tungsten_filament
    override val noteRes = R.string.card_note_tungsten_filament
    override val explainRes = R.string.card_explain_tungsten_filament

    override fun engrave(b: Burin) {
        // Le mur sombre.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            0xFF1E1812.toInt(), 0xFF0C0A08.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 340f, b.p)
        // Le culot à vis, en laiton.
        val base = Path().apply { addRoundRect(150f, 262f, 210f, 330f, 4f, 4f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(150f, 0f, 210f, 0f,
            intArrayOf(0xFF8A6A2A.toInt(), 0xFFE8C870.toInt(), 0xFF6A4A1A.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(base, b.p)
        for (k in 0 until 6) b.c.drawPath(Path().apply { moveTo(150f, 270f + k * 10f); quadTo(180f, 276f + k * 10f, 210f, 266f + k * 10f) }, b.pen(withAlpha(0xFF3A2A0A.toInt(), 200), 1.4f))
        b.stroke(base, .9f)
        // Le pied de verre, les deux fils d'amenée et le crochet du milieu.
        b.wash(Path().apply { moveTo(170f, 262f); lineTo(176f, 214f); lineTo(184f, 214f); lineTo(190f, 262f); close() }, 0xFFDDEEF0.toInt(), 70)
        b.c.drawPath(Path().apply { moveTo(176f, 216f); quadTo(150f, 190f, FL_X0, FL_Y) }, b.pen(0xFF9AA0A8.toInt(), 1.3f))
        b.c.drawPath(Path().apply { moveTo(184f, 216f); quadTo(210f, 190f, FL_X1, FL_Y) }, b.pen(0xFF9AA0A8.toInt(), 1.3f))
        b.line(180f, 214f, 180f, FL_Y + 12f, .8f, 0xFF9AA0A8.toInt())
        // Le verre de l'ampoule et ses reflets.
        val glass = Path().apply {
            moveTo(156f, 262f); cubicTo(150f, 236f, 96f, 220f, 92f, 160f); cubicTo(88f, 96f, 140f, 66f, 180f, 66f)
            cubicTo(220f, 66f, 272f, 96f, 268f, 160f); cubicTo(264f, 220f, 210f, 236f, 204f, 262f); close()
        }
        b.wash(glass, 0xFFDDEEF0.toInt(), 28)
        b.stroke(glass, 1.2f, withAlpha(0xFFDDEEF0.toInt(), 170))
        b.c.drawPath(Path().apply { moveTo(112f, 170f); quadTo(108f, 110f, 150f, 86f) }, b.pen(withAlpha(Ink.WHITE, 120), 3f))
        b.c.drawPath(Path().apply { moveTo(250f, 186f); quadTo(258f, 160f, 254f, 136f) }, b.pen(withAlpha(Ink.WHITE, 70), 2f))
    }

    // ───────────── animation ─────────────

    private val coil = Path().apply {
        val turns = 22
        val steps = turns * 14
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val phi = u * turns * 6.2831855f
            val x = FL_X0 + (FL_X1 - FL_X0) * u + 2.6f * cos(phi)
            val y = FL_Y + 10f * sin(3.1415927f * u) + 4.5f * sin(phi)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    private val wire = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(180f, FL_Y + 6f, 150f, intArrayOf(0xFFFFF4D8.toInt(), 0x80FFC870.toInt(), 0x00FF9A40),
            floatArrayOf(0f, .25f, 1f), Shader.TileMode.CLAMP)
    }
    private val room = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val tc = t % CYCLE
        val k = when {
            tc < 3f -> smooth(tc / 3f)
            tc < 6.5f -> 1f
            tc < 8.5f -> 1f - smooth((tc - 6.5f) / 2f)
            else -> 0f
        }
        // La pièce s'éclaire d'une lumière chaude.
        if (k > .3f) {
            val l = (k - .3f) / .7f
            room.color = withAlpha(0xFFFFD8A0.toInt(), (70 * l * l).toInt())
            c.drawRect(40f, 50f, 320f, 340f, room)
            halo.alpha = (255 * l).toInt()
            c.drawCircle(180f, FL_Y + 6f, 150f, halo)
        }
        // Le filament, de l'éteint au blanc.
        val color = heat(k)
        if (k > .15f) {
            wire.color = withAlpha(color, (120 * k).toInt()); wire.strokeWidth = 5f
            c.drawPath(coil, wire)
        }
        wire.color = color; wire.strokeWidth = 1.3f
        c.drawPath(coil, wire)
    }

    /** La couleur d'un fil chauffé : gris, rouge sombre, orange, jaune, blanc. */
    private fun heat(k: Float): Int {
        val i = (k * 4f).toInt().coerceAtMost(3)
        val f = k * 4f - i
        return mix(RAMP[i], RAMP[i + 1], f)
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val r = ((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * f).toInt()
        val g = ((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * f).toInt()
        val bl = ((a and 255) + ((b and 255) - (a and 255)) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun smooth(x: Float): Float {
        val q = x.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    private companion object {
        const val CYCLE = 10f
        const val FL_X0 = 132f
        const val FL_X1 = 228f
        const val FL_Y = 134f
        val RAMP = intArrayOf(0xFF4A4C50.toInt(), 0xFF8A1A08.toInt(), 0xFFFF6A10.toInt(), 0xFFFFC840.toInt(), 0xFFFFFBEA.toInt())
    }
}
