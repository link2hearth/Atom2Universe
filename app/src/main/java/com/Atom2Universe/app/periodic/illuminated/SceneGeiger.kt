package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * Le compteur Geiger, commun aux éléments radioactifs qu'on trouve dans la nature. La sonde passe
 * au-dessus d'un bloc de minerai : plus elle s'approche, plus le compteur crépite ; chaque éclair
 * du témoin est une particule qui traverse la sonde, et l'aiguille monte avec le comptage.
 * Seule la légende change d'un élément à l'autre.
 */
internal class SceneGeiger(
    override val noteRes: Int,
    override val nameRes: Int = R.string.card_scene_geiger_counter,
    override val explainRes: Int = R.string.card_explain_geiger_counter
) : EngravedScene() {

    override val sky = Sky.NONE

    override fun engrave(b: Burin) {
        // Le mur du laboratoire et la paillasse.
        b.wash(rect(40f, 50f, 320f, 244f), 0xFFB8C4B0.toInt(), 255)
        b.hatchRect(RectF(40f, 50f, 320f, 244f), 90f, 4.2f, .45f, withAlpha(Ink.BROWN, 110), Fade(0f, 244f, 0f, 60f, 200, 40))
        val bench = rect(40f, 244f, 320f, 340f)
        b.body(bench, 0xFF3A3A3E.toInt(), .3f, 4f, outline = 0f, washAlpha = 255)
        b.line(40f, 244f, 320f, 244f, 1.6f, 0xFF7A7A80.toInt())
        // Le bloc de minerai, croûte jaune et paillettes.
        val rock = poly(92f, 250f, 100f, 232f, 118f, 222f, 140f, 226f, 160f, 220f, 174f, 234f, 178f, 252f, 150f, 260f, 112f, 258f)
        b.wash(ellipse(136f, 258f, 50f, 6f), 0xFF000000.toInt(), 90)
        b.body(rock, 0xFF4A4440.toInt(), .55f, 30f, outline = 0f, washAlpha = 255)
        b.c.save(); b.c.clipPath(rock)
        b.wash(ellipse(130f, 228f, 22f, 8f), 0xFFC8C040.toInt(), 150)
        b.wash(ellipse(160f, 236f, 10f, 6f), 0xFFC8C040.toInt(), 130)
        b.stipple(rock, 90, .7f, 0xFFE8E070.toInt(), 6)
        b.c.restore()
        b.stroke(rock, 1f)
        b.line(118f, 222f, 124f, 244f, .6f); b.line(160f, 220f, 150f, 240f, .6f)
        // Le compteur : boîtier, cadran, témoin, grille du haut-parleur, bouton.
        val box = Path().apply { addRoundRect(214f, 190f, 306f, 262f, 6f, 6f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 190f, 0f, 262f,
            0xFFE8C040.toInt(), 0xFFB08A20.toInt(), Shader.TileMode.CLAMP)
        b.c.drawPath(box, b.p)
        b.tone(box, .25f, 70f, fade = Fade(306f, 0f, 240f, 0f, 200, 0))
        b.stroke(box, 1f)
        b.body(Path().apply { addRoundRect(244f, 196f, 300f, 232f, 3f, 3f, Path.Direction.CW) }, 0xFFF6F0DC.toInt(), 0f, outline = .8f, washAlpha = 255)
        for (k in 0..10) {
            val a = (-150f + k * 12f) * DEG
            val r0 = if (k % 5 == 0) 19f else 21f
            b.line(DIAL_X + r0 * cos(a), DIAL_Y + r0 * sin(a), DIAL_X + 24f * cos(a), DIAL_Y + 24f * sin(a), .7f)
        }
        b.c.drawArc(RectF(DIAL_X - 24f, DIAL_Y - 24f, DIAL_X + 24f, DIAL_Y + 24f), -60f, 30f, false, b.pen(Ink.RUBRIC, 1.6f))
        b.body(ellipse(LAMP_X, LAMP_Y, 5f, 5f), 0xFF5A1010.toInt(), 0f, outline = .7f, washAlpha = 255)
        for (j in 0 until 3) for (i in 0 until 4) b.wash(ellipse(254f + i * 8f, 242f + j * 7f, 2f, 2f), 0xFF2A2010.toInt(), 220)
        b.body(ellipse(228f, 248f, 7f, 7f), 0xFF2A2A2E.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.line(228f, 242f, 228f, 246f, 1f, Ink.WHITE)
    }

    override fun sprites() = listOf(
        // La sonde : un tube de métal, la fenêtre de mica en bas à gauche, la poignée noire.
        Sprite(PROBE, RectF(PX - 10f, PY - 30f, PX + 76f, PY + 10f)) { b ->
            val tube = limb(PX, PY, PX + 40f, PY - 10f, 14f, 14f)
            b.pen(0xFF000000.toInt()).shader = LinearGradient(PX, PY - 18f, PX + 4f, PY + 6f,
                intArrayOf(0xFFE8EAEE.toInt(), 0xFF8A8E94.toInt(), 0xFF4A4E54.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            b.c.drawPath(tube, b.p)
            b.stroke(tube, .8f)
            b.body(limb(PX + 40f, PY - 10f, PX + 70f, PY - 18f, 11f, 10f), 0xFF1E1E22.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.body(ellipse(PX, PY, 5f, 7f), 0xFFC8B070.toInt(), .2f, 90f, outline = .7f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cable = Path()
    private val tick = RectF()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La sonde va et vient au-dessus du minerai.
        val dx = 15f + 25f * sin(t * .5f)
        val dy = -4f + 26f * sin(t * .37f + 1f)
        val wx = PX + dx; val wy = PY + dy
        cable.reset()
        cable.moveTo(PX + 70f + dx, PY - 18f + dy)
        cable.quadTo(PX + 110f + dx * .5f, 270f, 214f, 236f)
        line.color = 0xFF1A1A1E.toInt(); line.strokeWidth = 2.2f
        c.drawPath(cable, line)
        s.draw(c, PROBE, dx, dy)

        // Le comptage dépend de la distance au minerai.
        val d = (ROCK_TOP - wy).coerceAtLeast(14f)
        val rate = 2f + 9000f / (d * d)
        val slot = (t / SLOT).toInt()
        val frac = t / SLOT - slot
        var flash = 0f
        for (j in 0 until 5) {
            val id = slot - j
            if (hash(id, 7) >= rate * SLOT) continue
            val age = (j + frac) * SLOT
            if (age > .08f) continue
            val k = 1f - age / .08f
            flash = max(flash, k)
            // La particule : un trait du minerai jusqu'à la fenêtre de la sonde.
            val sx = 104f + 66f * hash(id, 3); val sy = 232f + 10f * hash(id, 4)
            line.color = withAlpha(0xFFFFF4A0.toInt(), (200 * k).toInt()); line.strokeWidth = 1.1f
            c.drawLine(sx, sy, wx + (sx - wx) * .1f, wy + 4f, line)
        }
        if (flash > 0f) {
            fill.color = withAlpha(0xFFFF3020.toInt(), (240 * flash).toInt()); c.drawCircle(LAMP_X, LAMP_Y, 4f, fill)
            fill.color = withAlpha(0xFFFF3020.toInt(), (70 * flash).toInt()); c.drawCircle(LAMP_X, LAMP_Y, 11f, fill)
            line.color = withAlpha(Ink.SEPIA, (200 * flash).toInt()); line.strokeWidth = 1f
            for (r in floatArrayOf(10f, 15f)) {
                tick.set(266f - r, 249f - r, 266f + r, 249f + r)
                c.drawArc(tick, -40f, 80f, false, line)
            }
        }

        // L'aiguille suit le comptage, avec un petit tremblement.
        val n = (t * 8f).toInt(); val f = t * 8f - n
        val jitter = 4f * ((hash(n, 11) * (1f - f) + hash(n + 1, 11) * f) - .5f)
        val deg = -150f + 120f * (ln(rate) / ln(30f)).coerceIn(0f, 1f) + jitter
        val a = deg * DEG
        line.color = Ink.SEPIA; line.strokeWidth = 1.2f
        c.drawLine(DIAL_X, DIAL_Y, DIAL_X + 22f * cos(a), DIAL_Y + 22f * sin(a), line)
        fill.color = Ink.SEPIA; c.drawCircle(DIAL_X, DIAL_Y, 2f, fill)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val PX = 110f
        const val PY = 180f
        const val ROCK_TOP = 228f
        const val SLOT = 1f / 60f
        const val DIAL_X = 272f
        const val DIAL_Y = 230f
        const val LAMP_X = 230f
        const val LAMP_Y = 204f
        const val PROBE = 1
        const val DEG = .017453292f
    }
}
