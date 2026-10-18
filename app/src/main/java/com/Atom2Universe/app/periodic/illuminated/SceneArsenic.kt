package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Arsenic — le satellite. Allié au gallium, l'arsenic fait les cellules solaires les plus
 * efficaces : ce sont elles qui alimentent les satellites. Au-dessus de la Terre qui tourne
 * lentement, un satellite enveloppé de feuille d'or déploie ses ailes de cellules bleues, qui
 * pivotent pour suivre le Soleil et lancent un éclat quand elles lui font face.
 */
internal class SceneArsenic : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_satellite
    override val noteRes = R.string.card_note_satellite
    override val explainRes = R.string.card_explain_satellite

    private val earth = ellipse(EX, EY, ER, ER)

    override fun engrave(b: Burin) {
        // Le Soleil en haut à gauche, son halo.
        b.glow(84f, 104f, 120f, 0xFFFFF4D8.toInt(), 150)
        b.glow(84f, 104f, 26f, 0xFFFFFFFF.toInt(), 255)
        // La Terre : le bord bleu de l'atmosphère, l'océan dessous (les continents passent en animation).
        for (k in 0 until 6) {
            b.c.drawCircle(EX, EY, ER + 2f + k * 2.4f, b.pen(withAlpha(0xFF7AB8FF.toInt(), 120 - k * 20), 2.6f))
        }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, EY - ER, 0f, EY - ER + 70f,
            intArrayOf(0xFF3A78C8.toInt(), 0xFF1A4A8A.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(earth, b.p)
    }

    override fun sprites(): List<Sprite> = listOf(
        // Une bande de Terre deux fois plus large que la fenêtre : continents et nuages, qui défile.
        Sprite(LAND, RectF(0f, EY - ER - 2f, BAND, 340f)) { b ->
            val top = EY - ER
            // Chaque forme est posée trois fois, décalée d'une bande : le raccord ne se voit pas.
            for (off in floatArrayOf(-BAND, 0f, BAND)) {
                b.c.save(); b.c.translate(off, 0f)
                for (k in 0 until 7) {
                    val cx = 20f + k * 82f + 20f * hash(k, 1)
                    val cy = top + 20f + 30f * hash(k, 2)
                    val w = 26f + 30f * hash(k, 3)
                    val land = Path().apply {
                        moveTo(cx - w, cy)
                        cubicTo(cx - w * .8f, cy - 14f, cx - w * .2f, cy - 18f, cx + w * .3f, cy - 10f)
                        cubicTo(cx + w * .9f, cy - 12f, cx + w * 1.1f, cy + 6f, cx + w * .5f, cy + 14f)
                        cubicTo(cx, cy + 26f, cx - w * .6f, cy + 12f, cx - w, cy); close()
                    }
                    b.body(land, 0xFF5A8A4A.toInt(), .3f, 50f, outline = 0f, washAlpha = 255)
                    b.glow(cx + w * .2f, cy, w * .5f, 0xFFC8B070.toInt(), 120)
                }
                for (k in 0 until 14) {
                    val cx = 10f + k * 40f + 16f * hash(k, 4)
                    val cy = top + 10f + 50f * hash(k, 5)
                    b.stroke(Path().apply {
                        moveTo(cx - 22f, cy); cubicTo(cx - 8f, cy - 6f, cx + 8f, cy + 4f, cx + 24f, cy - 2f)
                    }, 3.4f + 2f * hash(k, 6), withAlpha(0xFFFFFFFF.toInt(), 170))
                }
                b.c.restore()
            }
        },
        // Le corps du satellite : feuille d'or froissée, parabole, mât, viseur d'étoiles.
        Sprite(BODY, RectF(-34f, -68f, 34f, 40f)) { b ->
            b.line(0f, -24f, 0f, -44f, 1.6f, 0xFFB8BEC4.toInt())
            val dish = Path().apply { moveTo(-26f, -44f); quadTo(0f, -66f, 26f, -44f); quadTo(0f, -50f, -26f, -44f); close() }
            b.body(dish, 0xFFF0F2F4.toInt(), .25f, 0f, outline = .9f, washAlpha = 255)
            b.line(0f, -50f, 0f, -60f, 1f, 0xFF8A9096.toInt())
            b.c.drawCircle(0f, -60f, 1.8f, b.pen(0xFF5A6066.toInt()))
            val box = Path().apply { addRect(-18f, -24f, 18f, 24f, Path.Direction.CW) }
            b.gold(box, Fade(-18f, -24f, 18f, 24f))
            b.hatch(box, 70f, 3.4f, .4f, Gilding.DEEP)
            for (k in 0 until 7) {
                val y = -20f + k * 6.4f
                b.stroke(Path().apply { moveTo(-18f, y); lineTo(-6f, y + 2f + 2f * hash(k, 7)); lineTo(6f, y - 1f); lineTo(18f, y + 1.6f) }, .6f, withAlpha(Gilding.LIGHT, 200))
            }
            b.stroke(box, 1f)
            b.body(Path().apply { addRect(-10f, 24f, 10f, 34f, Path.Direction.CW) }, 0xFF8A9096.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.body(ellipse(12f, -28f, 4f, 3f), 0xFF2A2C30.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
            // Les axes des ailes.
            b.line(-34f, 0f, -18f, 0f, 2.4f, 0xFF9AA0A6.toInt())
            b.line(18f, 0f, 34f, 0f, 2.4f, 0xFF9AA0A6.toInt())
        },
        // Une aile de cellules solaires vue de face, l'axe à gauche en (0, 0).
        Sprite(WING, RectF(-1f, -WING_H - 1f, WING_L + 1f, WING_H + 1f)) { b ->
            for (p in 0 until PANELS) {
                val x0 = 4f + p * (WING_L - 4f) / PANELS
                val x1 = x0 + (WING_L - 4f) / PANELS - 3f
                val panel = rect(x0, -WING_H, x1, WING_H)
                b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, -WING_H, 0f, WING_H,
                    intArrayOf(0xFF2A4A9A.toInt(), 0xFF142A6A.toInt(), 0xFF0E1E4E.toInt()), null, Shader.TileMode.CLAMP)
                b.c.drawPath(panel, b.p)
                val cell = b.pen(withAlpha(0xFF8AA8E0.toInt(), 150), .5f)
                var x = x0 + 5.6f
                while (x < x1) { b.c.drawLine(x, -WING_H, x, WING_H, cell); x += 5.6f }
                var y = -WING_H + 6f
                while (y < WING_H) { b.c.drawLine(x0, y, x1, y, cell); y += 6f }
                b.stroke(panel, .8f, 0xFFB8BEC4.toInt())
                b.line(x1, 0f, x1 + 3f, 0f, 1.6f, 0xFFB8BEC4.toInt())
            }
            b.line(0f, 0f, 4f, 0f, 2.4f, 0xFF9AA0A6.toInt())
        },
        // Le dos d'une aile : la toile blanche et ses renforts.
        Sprite(WING_BACK, RectF(-1f, -WING_H - 1f, WING_L + 1f, WING_H + 1f)) { b ->
            for (p in 0 until PANELS) {
                val x0 = 4f + p * (WING_L - 4f) / PANELS
                val x1 = x0 + (WING_L - 4f) / PANELS - 3f
                val panel = rect(x0, -WING_H, x1, WING_H)
                b.body(panel, 0xFFE4E6E8.toInt(), .25f, 0f, outline = .8f, washAlpha = 255)
                b.line(x0, 0f, x1, 0f, 1.2f, 0xFF9AA0A6.toInt())
                b.line((x0 + x1) / 2f, -WING_H, (x0 + x1) / 2f, WING_H, 1f, 0xFF9AA0A6.toInt())
            }
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La Terre tourne : sa bande de continents défile, découpée au disque.
        val shift = (t * 7f) % BAND
        c.save(); c.clipPath(earth)
        s.draw(c, LAND, dx = -shift)
        s.draw(c, LAND, dx = BAND - shift)
        c.restore()
        // Le satellite dérive à peine ; ses ailes pivotent autour de leur axe pour suivre le Soleil.
        val x = SX + 6f * sin(t * .21f)
        val y = SY + 4f * sin(t * .27f)
        val roll = 2f * sin(t * .17f)
        val phi = .25f + 1.6f * sin(t * .32f)
        val face = cos(phi)
        c.save(); c.translate(x, y); c.rotate(roll)
        wing(c, s, -34f, -1f, face)
        wing(c, s, 34f, 1f, face)
        s.draw(c, BODY)
        // Le feu de position clignote au bout du mât.
        if ((t % 1.6f) < .14f) {
            fill.color = 0xFFFF4A3A.toInt(); c.drawCircle(0f, -60f, 2.2f, fill)
            fill.color = withAlpha(0xFFFF4A3A.toInt(), 70); c.drawCircle(0f, -60f, 7f, fill)
        }
        // Une bouffée de propulseur, de temps en temps.
        val puff = (t % 9f) - 6f
        if (puff in 0f..1f) {
            for (k in 0 until 5) {
                val d = puff * (20f + k * 6f)
                fill.color = withAlpha(0xFFFFFFFF.toInt(), (150 * (1f - puff)).toInt())
                c.drawCircle(-10f - d * .3f, 34f + d, 2f + puff * 5f + k, fill)
            }
        }
        c.restore()
    }

    /** Une aile en ([x0], 0), vers la droite si [dir] vaut 1 ; aplatie selon l'angle, éclat face au Soleil. */
    private fun wing(c: Canvas, s: Sprites, x0: Float, dir: Float, face: Float) {
        c.save(); c.translate(x0, 0f); c.scale(dir, abs(face).coerceAtLeast(.03f))
        s.draw(c, if (face >= 0f) WING else WING_BACK)
        val glint = (face - .9f) / .1f
        if (glint > 0f) {
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (150 * glint).toInt())
            c.drawRect(4f, -WING_H, WING_L, WING_H, fill)
        }
        c.restore()
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** La Terre : un grand disque dont on ne voit que le haut. */
        const val EX = 180f
        const val EY = 600f
        const val ER = 340f
        const val BAND = 560f
        /** Le satellite. */
        const val SX = 186f
        const val SY = 168f
        const val WING_L = 92f
        const val WING_H = 15f
        const val PANELS = 4
        const val LAND = 1
        const val BODY = 2
        const val WING = 3
        const val WING_BACK = 4
    }
}
