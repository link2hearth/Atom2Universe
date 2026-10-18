package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Argon — l'ampoule à incandescence. Le filament de tungstène chauffe à blanc ; dans l'air il
 * brûlerait aussitôt, alors on remplit l'ampoule d'argon, un gaz qui ne réagit avec rien.
 * La lampe s'allume, le filament passe du rouge au blanc, la pièce s'éclaire et un papillon
 * de nuit vient tourner autour.
 */
internal class SceneArgon : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_light_bulb
    override val noteRes = R.string.card_note_light_bulb
    override val explainRes = R.string.card_explain_light_bulb

    override fun engrave(b: Burin) {
        room(b)
        table(b)
        bulb(b)
    }

    private fun room(b: Burin) {
        val wall = rect(40f, 56f, 320f, 280f)
        b.fill(wall, 0xFF2E2C3A.toInt())
        b.hatch(wall, 0f, 1.7f, .5f, 0xFF15141E.toInt())
        // Le papier peint : des rayures et un petit semis de fleurs.
        var x = 52f
        while (x < 320f) { b.c.drawRect(x, 56f, x + 5f, 280f, b.pen(withAlpha(0xFF4A4658.toInt(), 120))); x += 22f }
        var row = 0
        var y = 74f
        while (y < 276f) {
            var fx = 63f + (if (row % 2 == 0) 0f else 11f)
            while (fx < 320f) { b.c.drawCircle(fx, y, 1.2f, b.pen(withAlpha(0xFF6A6478.toInt(), 160))); fx += 22f }
            y += 18f; row++
        }
        // La plinthe.
        b.body(rect(40f, 272f, 320f, 280f), 0xFF3A3040.toInt(), .4f, 0f, outline = .6f, washAlpha = 255)
    }

    private fun table(b: Burin) {
        val top = poly(40f, 284f, 320f, 284f, 320f, 340f, 40f, 340f)
        b.body(top, 0xFF7A5234.toInt(), .45f, 0f, Fade(0f, 284f, 0f, 320f, 120, 255), outline = .8f, washAlpha = 255)
        for (i in 0 until 6) {
            val gy = 288f + i * 4.6f
            b.stroke(Path().apply { moveTo(40f, gy); cubicTo(120f, gy - 2f, 220f, gy + 3f, 320f, gy - 1f) }, .5f, withAlpha(0xFF3A2414.toInt(), 200))
        }
        // Un livre ouvert, un encrier et sa plume.
        val left = Path().apply { moveTo(96f, 300f); cubicTo(112f, 294f, 128f, 294f, 140f, 298f); lineTo(140f, 312f); cubicTo(128f, 308f, 112f, 308f, 92f, 314f); close() }
        val right = Path().apply { moveTo(140f, 298f); cubicTo(152f, 294f, 168f, 294f, 184f, 300f); lineTo(188f, 314f); cubicTo(168f, 308f, 152f, 308f, 140f, 312f); close() }
        for (page in listOf(left, right)) b.body(page, 0xFFF4ECD8.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
        for (k in 0 until 4) {
            b.line(102f, 301f + k * 2.6f, 134f, 300f + k * 2.6f, .4f, withAlpha(Ink.SEPIA, 150))
            b.line(146f, 300f + k * 2.6f, 178f, 301f + k * 2.6f, .4f, withAlpha(Ink.SEPIA, 150))
        }
        b.body(ellipse(240f, 302f, 12f, 4f), 0xFF22202A.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(rect(230f, 290f, 250f, 302f), 0xFF22202A.toInt(), .4f, 90f, Fade(250f, 0f, 236f, 0f, 230, 0), outline = .7f, washAlpha = 255)
        b.body(ellipse(240f, 290f, 10f, 3f), 0xFF3A3848.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        b.taper(242f, 290f, 250f, 276f, 258f, 262f, 270f, 250f, 3f, 0xFFF4ECD8.toInt())
        b.line(242f, 290f, 268f, 252f, .5f)
    }

    private fun bulb(b: Burin) {
        // Le fil torsadé qui descend du plafond.
        val twist = b.pen(0xFF8A7A5A.toInt(), 1.6f)
        var y = 56f
        while (y < 96f) { b.c.drawLine(178.5f, y, 181.5f, y + 3f, twist); b.c.drawLine(181.5f, y + 3f, 178.5f, y + 6f, twist); y += 6f }
        // La douille de laiton et le culot à vis.
        val socket = Path().apply { addRoundRect(166f, 94f, 194f, 120f, 3f, 3f, Path.Direction.CW) }
        b.body(socket, 0xFFB08A3A.toInt(), .35f, 90f, Fade(194f, 0f, 174f, 0f, 240, 0), outline = .9f, washAlpha = 255)
        b.line(166f, 104f, 194f, 104f, .7f)
        for (k in 0 until 4) {
            val ty = 122f + k * 4f
            b.body(ellipse(180f, ty, 11f - k * .3f, 2.4f), 0xFFC8CCD2.toInt(), .3f, 0f, Fade(191f, 0f, 172f, 0f, 220, 0), outline = .6f, washAlpha = 255)
        }
        // Le verre : transparent, à peine teinté, avec ses reflets.
        val glass = Path().apply {
            moveTo(170f, 136f); cubicTo(170f, 146f, 136f, 160f, 132f, 196f)
            cubicTo(128f, 232f, 156f, 252f, 180f, 252f); cubicTo(204f, 252f, 232f, 232f, 228f, 196f)
            cubicTo(224f, 160f, 190f, 146f, 190f, 136f); close()
        }
        b.wash(glass, 0xFFE8ECF4.toInt(), 40)
        b.hatch(glass, 60f, 2.6f, .35f, withAlpha(0xFFFFFFFF.toInt(), 90), Fade(228f, 230f, 180f, 196f, 220, 0))
        // Le pied de verre, les deux fils d'amenée et le crochet du milieu.
        val stem = Path().apply { moveTo(174f, 138f); lineTo(186f, 138f); lineTo(184f, 170f); cubicTo(184f, 176f, 176f, 176f, 176f, 170f); close() }
        b.wash(stem, 0xFFF0F2F6.toInt(), 120)
        b.stroke(stem, .5f, withAlpha(0xFFB8C0CC.toInt(), 255))
        val wire = withAlpha(0xFF8A8A92.toInt(), 255)
        b.stroke(Path().apply { moveTo(177f, 140f); lineTo(177f, 168f); cubicTo(170f, 176f, 162f, 180f, FIL_L, FIL_Y) }, .8f, wire)
        b.stroke(Path().apply { moveTo(183f, 140f); lineTo(183f, 168f); cubicTo(190f, 176f, 198f, 180f, FIL_R, FIL_Y) }, .8f, wire)
        b.line(180f, 174f, 180f, FIL_Y + 6f, .6f, wire)
        b.stroke(glass, 1f, withAlpha(Ink.SEPIA, 230))
        b.stroke(Path().apply { moveTo(146f, 176f); cubicTo(140f, 190f, 140f, 210f, 146f, 222f) }, 2.4f, withAlpha(0xFFFFFFFF.toInt(), 170))
        b.c.drawCircle(152f, 168f, 2f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200)))
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(HALO, RectF(-120f, -120f, 120f, 120f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 120f,
                intArrayOf(0xEEFFF6D8.toInt(), 0x88FFD890.toInt(), 0x30FFB050, 0x00FFB050), floatArrayOf(0f, .18f, .5f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 120f, b.p)
        },
        Sprite(MOTH, RectF(-9f, -7f, 9f, 7f)) { b ->
            for (side in floatArrayOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(0f, 0f); cubicTo(side * 4f, -7f, side * 9f, -5f, side * 8.5f, 0f)
                    cubicTo(side * 8f, 4f, side * 3f, 5f, 0f, 1.5f); close()
                }
                b.body(wing, 0xFFB8A888.toInt(), .45f, 30f, outline = .5f, washAlpha = 255)
                b.c.drawCircle(side * 5f, -1.5f, 1f, b.pen(Ink.SEPIA))
            }
            b.body(ellipse(0f, 1f, 1.4f, 4f), 0xFF6A5A44.toInt(), .3f, 90f, outline = .5f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val u = t % CYCLE
        val heat = when {
            u < .6f -> 0f
            u < 1.4f -> (u - .6f) / .8f
            u < 8.4f -> 1f - .03f * (.5f + .5f * sin(t * 37f)) * (.5f + .5f * sin(t * 5.3f))
            else -> (1f - (u - 8.4f) / .35f).coerceAtLeast(0f)
        }
        val light = heat * heat
        // La pièce s'éclaire : un grand halo chaud, puis sa tache sur la table.
        if (light > .01f) {
            c.save(); c.translate(180f, 196f); c.scale(1.5f, 1.5f)
            s.draw(c, HALO, alpha = (235 * light).toInt())
            c.restore()
            c.save(); c.translate(180f, 300f); c.scale(1.5f, .32f)
            s.draw(c, HALO, alpha = (170 * light).toInt())
            c.restore()
        }
        // Le filament : rouge sombre, orange, puis blanc.
        val color = filamentColor(heat)
        if (heat > .02f) {
            ink.color = withAlpha(color, (120 * heat).toInt()); ink.strokeWidth = 5f
            c.drawLines(coil, ink)
        }
        ink.color = if (heat > .02f) color else 0xFF5A4A44.toInt(); ink.strokeWidth = 1.2f
        c.drawLines(coil, ink)
        // Le papillon de nuit tourne autour de la lumière, et s'en va dans le noir.
        val a = t * 1.7f + .8f * sin(t * 2.3f)
        val r = 62f + 12f * sin(t * 1.1f)
        val mx = 180f + cos(a) * r; val my = 190f + sin(a) * r * .55f + 6f * sin(t * 4f)
        val flap = .35f + .65f * kotlin.math.abs(sin(t * 26f))
        c.save(); c.translate(mx, my); c.rotate(Math.toDegrees((a + PI / 2).toDouble()).toFloat() * .25f); c.scale(flap, 1f)
        s.draw(c, MOTH, alpha = (110 + 145 * light).toInt())
        c.restore()
    }

    private fun filamentColor(h: Float): Int {
        val stops = intArrayOf(0xFF6A1A10.toInt(), 0xFFFF7A2A.toInt(), 0xFFFFF6DC.toInt())
        val x = (h * 2f).coerceIn(0f, 2f)
        val i = x.toInt().coerceAtMost(1)
        val f = x - i
        val c0 = stops[i]; val c1 = stops[i + 1]
        fun ch(sh: Int) = (((c0 shr sh) and 255) * (1 - f) + ((c1 shr sh) and 255) * f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** La spirale du filament, tendue entre les deux fils, vue de côté. */
    private val coil = FloatArray(COIL * 4).also { a ->
        var lx = FIL_L; var ly = FIL_Y
        for (k in 1..COIL) {
            val u = k / COIL.toFloat()
            val x = FIL_L + (FIL_R - FIL_L) * u
            val y = FIL_Y + 2.4f * sin(u * 26f * PI.toFloat()) + 3f * sin(u * PI.toFloat())
            a[(k - 1) * 4] = lx; a[(k - 1) * 4 + 1] = ly; a[(k - 1) * 4 + 2] = x; a[(k - 1) * 4 + 3] = y
            lx = x; ly = y
        }
    }

    private companion object {
        const val FIL_L = 158f
        const val FIL_R = 202f
        const val FIL_Y = 184f
        const val COIL = 104
        const val CYCLE = 9f
        const val HALO = 1
        const val MOTH = 2
    }
}
