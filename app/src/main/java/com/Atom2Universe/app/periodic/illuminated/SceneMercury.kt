package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mercure — le thermomètre. Le seul métal liquide à température ordinaire : il se dilate quand
 * il fait chaud et monte dans le tube. Le soleil sort des nuages par la fenêtre, la colonne
 * monte ; un nuage passe, elle redescend. Dans la coupelle, des perles de mercure tremblent.
 */
internal class SceneMercury : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_thermometer
    override val noteRes = R.string.card_note_thermometer
    override val explainRes = R.string.card_explain_thermometer

    override fun engrave(b: Burin) {
        wall(b)
        window(b)
        thermometer(b)
        dish(b)
    }

    private fun wall(b: Burin) {
        val wall = rect(40f, 56f, 320f, 340f)
        b.fill(wall, 0xFFE6D6B4.toInt())
        // Des lambris : planches verticales, plus sombres vers le bas.
        var x = 40f
        while (x < 320f) { b.line(x, 56f, x, 340f, .6f, withAlpha(Ink.BROWN, 150)); x += 20f }
        b.hatch(wall, 90f, 2.4f, .4f, withAlpha(Ink.BROWN, 140), Fade(0f, 120f, 0f, 330f, 0, 200))
        b.glow(250f, 150f, 120f, 0xFFFFF0C8.toInt(), 120)
    }

    private fun window(b: Burin) {
        val glass = rect(WIN_L, WIN_T, WIN_R, WIN_B)
        b.washGradient(glass, 0xFF8CB4D8.toInt(), 0f, WIN_T, 255, 0f, WIN_B, 120)
        b.hatchRect(RectF(WIN_L, WIN_T, WIN_R, WIN_B), 0f, 2.6f, .4f, withAlpha(Ink.BROWN, 150), Fade(0f, WIN_T, 0f, WIN_B, 180, 0))
        // Des toits au loin, sous le ciel.
        val roofs = Path().apply {
            moveTo(WIN_L, 214f); lineTo(214f, 206f); lineTo(226f, 198f); lineTo(238f, 206f); lineTo(252f, 206f)
            lineTo(252f, 196f); lineTo(258f, 196f); lineTo(258f, 206f); lineTo(272f, 202f); lineTo(286f, 210f); lineTo(WIN_R, 208f)
            lineTo(WIN_R, WIN_B); lineTo(WIN_L, WIN_B); close()
        }
        b.body(roofs, 0xFF9AA0A8.toInt(), .4f, 30f, outline = .6f, washAlpha = 220)
        // Le cadre, la croisée et l'appui.
        val frame = Path().apply {
            addRect(WIN_L - 7f, WIN_T - 7f, WIN_R + 7f, WIN_B + 7f, Path.Direction.CW)
            addRect(WIN_L, WIN_T, WIN_R, WIN_B, Path.Direction.CCW)
        }
        b.body(frame, 0xFFF0EADC.toInt(), .3f, 0f, Fade(WIN_R + 7f, 0f, WIN_L, 0f, 200, 40), outline = .9f, washAlpha = 255)
        b.body(rect((WIN_L + WIN_R) / 2f - 2.5f, WIN_T, (WIN_L + WIN_R) / 2f + 2.5f, WIN_B), 0xFFF0EADC.toInt(), .25f, 90f, outline = .7f, washAlpha = 255)
        b.body(rect(WIN_L, 150f, WIN_R, 155f), 0xFFF0EADC.toInt(), .25f, 0f, outline = .7f, washAlpha = 255)
        b.body(rect(WIN_L - 14f, WIN_B + 7f, WIN_R + 14f, WIN_B + 15f), 0xFFE8DCC4.toInt(), .35f, 0f, Fade(0f, WIN_B + 7f, 0f, WIN_B + 15f, 60, 230), outline = .8f, washAlpha = 255)
        // Les rideaux, de part et d'autre.
        for (side in floatArrayOf(-1f, 1f)) {
            val edge = if (side < 0) WIN_L - 4f else WIN_R + 4f
            val out = edge + side * 26f
            val curtain = Path().apply {
                moveTo(edge, WIN_T - 14f); lineTo(out, WIN_T - 14f); lineTo(out, WIN_B + 30f)
                cubicTo(out - side * 8f, WIN_B + 26f, edge + side * 6f, WIN_B - 30f, edge + side * 10f, 150f)
                cubicTo(edge + side * 2f, 120f, edge, 100f, edge, WIN_T - 14f); close()
            }
            b.wash(curtain, 0xFFA8443A.toInt(), 240)
            b.c.save(); b.c.clipPath(curtain)
            for (k in 0 until 5) {
                val fx = edge + side * (4f + k * 5f)
                b.c.drawRect(fx, WIN_T - 14f, fx + side * 2f, WIN_B + 34f, b.pen(withAlpha(0xFF5A1A16.toInt(), 120)))
            }
            b.c.restore()
            b.hatch(curtain, 90f, 1.5f, .4f, Ink.SEPIA, Fade(out, 0f, edge, 0f, 220, 30))
            b.stroke(curtain, .8f)
        }
        b.body(rect(WIN_L - 36f, WIN_T - 18f, WIN_R + 36f, WIN_T - 13f), 0xFF8A6A3A.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
    }

    private fun thermometer(b: Burin) {
        // La planchette de bois, chantournée en haut.
        val board = Path().apply {
            moveTo(96f, 300f); lineTo(96f, 96f); cubicTo(96f, 80f, 106f, 72f, 118f, 72f)
            cubicTo(130f, 72f, 140f, 80f, 140f, 96f); lineTo(140f, 300f); close()
        }
        b.c.drawRect(100f, 76f, 146f, 306f, b.pen(withAlpha(Ink.SEPIA, 60)))
        b.body(board, 0xFFB8875A.toInt(), .3f, 90f, Fade(140f, 0f, 104f, 0f, 220, 30), outline = .9f, washAlpha = 255)
        b.c.drawCircle(118f, 84f, 2.2f, b.pen(0xFFC8CCD2.toInt()))
        b.c.drawCircle(118f, 84f, 2.2f, b.pen(Ink.SEPIA, .5f))
        // Les graduations, de part et d'autre du tube.
        var k = 0
        var y = BULB_Y - 18f
        while (y > TUBE_TOP + 4f) {
            val long = k % 5 == 0
            val w = if (long) 8f else 4.5f
            b.line(114f - w, y, 113f, y, if (long) .8f else .5f)
            b.line(123f, y, 123f + w, y, if (long) .8f else .5f)
            y -= 5.2f; k++
        }
        // Le trait rouge de la température du corps.
        b.line(105f, MARK_Y, 113f, MARK_Y, 1.2f, Ink.RUBRIC); b.line(123f, MARK_Y, 131f, MARK_Y, 1.2f, Ink.RUBRIC)
        // Le tube de verre et le réservoir.
        val tube = Path().apply { addRoundRect(115f, TUBE_TOP, 121f, BULB_Y, 3f, 3f, Path.Direction.CW) }
        b.wash(tube, 0xFFF4F6F8.toInt(), 200)
        b.stroke(tube, .7f)
        b.c.drawCircle(118f, BULB_Y, 9f, b.pen(0xFFF4F6F8.toInt()))
        b.c.drawCircle(118f, BULB_Y, 9f, b.pen(Ink.SEPIA, .8f))
    }

    /** La coupelle de porcelaine, sur l'appui de la fenêtre. */
    private fun dish(b: Burin) {
        b.c.save(); b.c.translate(0f, DISH_DY)
        val bowl = Path().apply {
            moveTo(214f, 266f); cubicTo(216f, 282f, 286f, 282f, 288f, 266f)
            cubicTo(278f, 270f, 224f, 270f, 214f, 266f); close()
        }
        b.c.drawOval(212f, 274f, 292f, 284f, b.pen(withAlpha(Ink.SEPIA, 60)))
        b.body(bowl, 0xFFF6F4EE.toInt(), .3f, 0f, Fade(0f, 280f, 0f, 268f, 230, 20), outline = .8f, washAlpha = 255)
        b.body(ellipse(251f, 266f, 37f, 4.6f), 0xFFE8E6E0.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        b.c.drawArc(222f, 263f, 280f, 269f, 200f, 50f, false, b.pen(Ink.RUBRIC, .8f))
        b.c.restore()
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(CLOUD, RectF(0f, 0f, 92f, 40f)) { b -> cloud(b) },
        Sprite(SUN, RectF(-30f, -30f, 30f, 30f)) { b ->
            b.glow(0f, 0f, 30f, 0xFFFFF2B0.toInt(), 220)
            b.c.drawCircle(0f, 0f, 11f, b.pen(0xFFFFE07A.toInt()))
            b.c.drawCircle(0f, 0f, 11f, b.pen(Ink.BROWN, .6f))
        },
        Sprite(BEAD, RectF(-6f, -6f, 6f, 6f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(-1.6f, -2f, 6f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFB8BEC8.toInt(), 0xFF4A5058.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 5f, b.p)
            b.c.drawCircle(0f, 0f, 5f, b.pen(Ink.SEPIA, .5f))
        }
    )

    private fun cloud(b: Burin) {
        val rnd = Random(6)
        val shape = Path()
        for (i in 0 until 5) {
            val x = 18f + i * 14f
            val r = 40f * (.24f + rnd.nextFloat() * .14f) * (1f - abs(i - 2) * .14f)
            shape.addCircle(x, 26f - r * .55f, r, Path.Direction.CW)
        }
        shape.addRect(14f, 18f, 78f, 26f, Path.Direction.CW)
        val solid = Path().apply { op(shape, Path.Op.UNION) }
        b.fill(solid, 0xFFF6F2EA.toInt())
        b.hatch(solid, 0f, 2f, .45f, Ink.BROWN, Fade(0f, 26f, 0f, 8f, 210, 0))
        b.stroke(solid, .6f, Ink.BROWN)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val beam = Path().apply {
        moveTo(WIN_L + 4f, WIN_B); lineTo(WIN_R - 4f, WIN_B); lineTo(WIN_R - 30f, 340f); lineTo(WIN_L - 70f, 340f); close()
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val sun = 1f - cover(t)
        // Le ciel, le soleil et le nuage qui passe, vus par la vitre.
        c.save(); c.clipRect(WIN_L, WIN_T, WIN_R, WIN_B)
        c.save(); c.translate(SUN_X, SUN_Y); s.draw(c, SUN); c.restore()
        s.draw(c, CLOUD, dx = cloudX(t) - 46f, dy = SUN_Y - 22f)
        s.draw(c, CLOUD, dx = cloudX(t + 13f) - 46f, dy = WIN_T + 2f, alpha = 200)
        c.restore()
        // Le rayon de soleil qui entre dans la pièce.
        fill.color = withAlpha(0xFFFFF0C0.toInt(), (70 * sun).toInt())
        c.drawPath(beam, fill)
        // La colonne de mercure : elle suit le soleil avec un peu de retard.
        val warm = 1f - cover(t - 2.5f)
        val level = BULB_Y - 26f - (BULB_Y - TUBE_TOP - 50f) * (.35f + .5f * warm + .04f * sin(t * .7f))
        fill.color = 0xFFAEB4BE.toInt()
        c.drawRect(116.4f, level, 119.6f, BULB_Y, fill)
        c.drawCircle(118f, BULB_Y, 7.4f, fill)
        c.drawCircle(118f, level, 1.6f, fill)
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 200)
        c.drawRect(117f, level + 2f, 117.8f, BULB_Y - 6f, fill)
        c.drawCircle(115.5f, BULB_Y - 3f, 2.2f, fill)
        // Les perles de mercure tremblent et accrochent la lumière.
        for (i in BEADS.indices step 3) {
            val bx = BEADS[i]; val by = BEADS[i + 1] + DISH_DY; val r = BEADS[i + 2]
            val w = sin(t * (5f + i * .3f) + i)
            c.save(); c.translate(bx, by); c.scale(r / 5f * (1f + .05f * w), r / 5f * .78f * (1f - .05f * w))
            s.draw(c, BEAD)
            c.restore()
            if (sun > .5f && ((t * .8f + i * .19f) % 1f) < .12f) {
                ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * sun).toInt()); ink.strokeWidth = .7f
                val g = r * .9f
                c.drawLine(bx - r * .3f - g, by - r * .4f, bx - r * .3f + g, by - r * .4f, ink)
                c.drawLine(bx - r * .3f, by - r * .4f - g, bx - r * .3f, by - r * .4f + g, ink)
            }
        }
    }

    /** Le nuage traverse la fenêtre ; [cover] dit combien il cache le soleil. */
    private fun cloudX(t: Float) = (t * 7f) % 240f + WIN_L - 70f

    private fun cover(t: Float): Float {
        val d = (cloudX(t) - SUN_X) / 34f
        return exp(-d * d)
    }

    private companion object {
        const val WIN_L = 206f
        const val WIN_R = 296f
        const val WIN_T = 92f
        const val WIN_B = 226f
        const val SUN_X = 262f
        const val SUN_Y = 124f
        const val TUBE_TOP = 92f
        const val BULB_Y = 282f
        const val MARK_Y = 160f
        const val DISH_DY = -36f
        const val CLOUD = 1
        const val SUN = 2
        const val BEAD = 3
        /** Les perles : x, y, rayon. */
        val BEADS = floatArrayOf(236f, 264f, 4.2f, 250f, 266f, 6f, 262f, 263f, 3f, 270f, 266f, 4f, 244f, 268f, 2.2f, 278f, 264f, 2.4f)
    }
}
