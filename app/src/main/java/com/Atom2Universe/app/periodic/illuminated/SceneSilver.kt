package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Argent — l'argenterie, à la lueur des bougies. L'argent est le métal qui renvoie le mieux la
 * lumière : chandelier, théière et couverts brillent dans la pénombre. Les flammes vacillent et
 * les reflets dansent sur le métal.
 */
internal class SceneSilver : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_silverware
    override val noteRes = R.string.card_note_silverware
    override val explainRes = R.string.card_explain_silverware

    override fun engrave(b: Burin) {
        room(b)
        candelabra(b)
        teapot(b)
        cutlery(b)
    }

    private fun room(b: Burin) {
        val wall = rect(40f, 56f, 320f, 250f)
        b.fill(wall, 0xFF22302A.toInt())
        b.hatch(wall, 0f, 1.8f, .5f, 0xFF0E1612.toInt())
        // Le damas du papier peint : un semis de losanges.
        var row = 0
        var y = 70f
        while (y < 246f) {
            var x = 52f + (if (row % 2 == 0) 0f else 14f)
            while (x < 320f) {
                b.stroke(poly(x, y - 5f, x + 4f, y, x, y + 5f, x - 4f, y), .5f, withAlpha(0xFF4A5E52.toInt(), 200))
                x += 28f
            }
            y += 16f; row++
        }
        val table = rect(40f, 248f, 320f, 340f)
        b.body(table, 0xFF4A2C1C.toInt(), .55f, 0f, Fade(0f, 248f, 0f, 330f, 100, 255), outline = .8f, washAlpha = 255)
        b.line(40f, 250f, 320f, 250f, 1.2f, withAlpha(0xFFB8885A.toInt(), 160))
    }

    /** Un dégradé d'argent poli : sombre, clair, sombre, avec un éclat. */
    private fun silver(b: Burin, shape: Path, x0: Float, x1: Float) {
        b.pen(0xFF000000.toInt()).shader = LinearGradient(x0, 0f, x1, 0f,
            intArrayOf(0xFF4A4E56.toInt(), 0xFFE8ECF2.toInt(), 0xFF8A909A.toInt(), 0xFFF8FAFC.toInt(), 0xFF3A3E46.toInt()),
            floatArrayOf(0f, .28f, .5f, .7f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(shape, b.p)
        b.hatch(shape, 90f, 1.6f, .4f, Ink.SEPIA, Fade(x1, 0f, x1 - (x1 - x0) * .3f, 0f, 200, 0))
        b.stroke(shape, .8f)
    }

    private fun candelabra(b: Burin) {
        // Le pied, la tige à nœuds, les deux bras recourbés.
        silver(b, ellipse(CX, 286f, 30f, 7f), CX - 30f, CX + 30f)
        silver(b, Path().apply { moveTo(CX - 26f, 284f); cubicTo(CX - 18f, 270f, CX - 8f, 268f, CX - 5f, 256f); lineTo(CX + 5f, 256f); cubicTo(CX + 8f, 268f, CX + 18f, 270f, CX + 26f, 284f); close() }, CX - 26f, CX + 26f)
        silver(b, rect(CX - 4f, 168f, CX + 4f, 258f), CX - 4f, CX + 4f)
        for (ny in floatArrayOf(250f, 214f, 176f)) silver(b, ellipse(CX, ny, 8f, 4f), CX - 8f, CX + 8f)
        for (side in floatArrayOf(-1f, 1f)) {
            val arm = Path().apply {
                moveTo(CX, 206f); cubicTo(CX + side * 20f, 210f, CX + side * 40f, 206f, CX + side * 40f, 186f)
            }
            b.stroke(arm, 6f, Ink.SEPIA)
            b.stroke(arm, 4.4f, 0xFFB8BEC8.toInt())
            b.stroke(arm, 1.2f, 0xFFF4F6FA.toInt())
            silver(b, Path().apply { addRoundRect(CX + side * 40f - 9f, 180f, CX + side * 40f + 9f, 188f, 2f, 2f, Path.Direction.CW) }, CX + side * 40f - 9f, CX + side * 40f + 9f)
        }
        silver(b, Path().apply { addRoundRect(CX - 10f, 160f, CX + 10f, 170f, 2f, 2f, Path.Direction.CW) }, CX - 10f, CX + 10f)
        // Les bougies, cire blanche, une coulure.
        for (k in CANDLES.indices step 3) {
            val x = CANDLES[k]; val top = CANDLES[k + 1]; val bottom = CANDLES[k + 2]
            val wax = rect(x - 4.5f, top, x + 4.5f, bottom)
            b.body(wax, 0xFFF6F0E2.toInt(), .3f, 90f, Fade(x + 4.5f, 0f, x - 1f, 0f, 220, 0), outline = .7f, washAlpha = 255)
            b.taper(x + 2f, top + 1f, x + 3f, top + 6f, x + 2.5f, top + 10f, x + 3f, top + 14f, 2.2f, 0xFFEDE4D0.toInt())
            b.line(x, top, x, top - 4f, .9f, Ink.SEPIA)
        }
    }

    private fun teapot(b: Burin) {
        val x = 254f; val y = 262f
        b.c.drawOval(x - 36f, y + 22f, x + 40f, y + 30f, b.pen(withAlpha(0xFF000000.toInt(), 110)))
        val spout = Path().apply {
            moveTo(x - 22f, y + 4f); cubicTo(x - 34f, y, x - 40f, y - 14f, x - 48f, y - 22f)
            lineTo(x - 44f, y - 25f); cubicTo(x - 36f, y - 18f, x - 30f, y - 10f, x - 20f, y - 8f); close()
        }
        silver(b, spout, x - 48f, x - 20f)
        val handle = Path().apply {
            moveTo(x + 24f, y - 16f); cubicTo(x + 46f, y - 20f, x + 50f, y + 8f, x + 26f, y + 12f)
        }
        b.stroke(handle, 6f, Ink.SEPIA); b.stroke(handle, 4.2f, 0xFFB8BEC8.toInt()); b.stroke(handle, 1f, 0xFFF4F6FA.toInt())
        val body = Path().apply {
            moveTo(x - 20f, y - 20f); cubicTo(x - 34f, y - 10f, x - 34f, y + 20f, x - 18f, y + 24f)
            lineTo(x + 18f, y + 24f); cubicTo(x + 34f, y + 20f, x + 34f, y - 10f, x + 20f, y - 20f); close()
        }
        silver(b, body, x - 30f, x + 30f)
        b.stroke(Path().apply { moveTo(x - 30f, y + 2f); cubicTo(x - 10f, y + 8f, x + 10f, y + 8f, x + 30f, y + 2f) }, .8f, withAlpha(Ink.SEPIA, 200))
        silver(b, ellipse(x, y - 20f, 20f, 4f), x - 20f, x + 20f)
        silver(b, Path().apply { moveTo(x - 14f, y - 21f); cubicTo(x - 12f, y - 32f, x + 12f, y - 32f, x + 14f, y - 21f); close() }, x - 14f, x + 14f)
        silver(b, ellipse(x, y - 33f, 3.6f, 3f), x - 4f, x + 4f)
    }

    private fun cutlery(b: Burin) {
        // Une serviette blanche, puis une fourchette et une cuillère posées en biais.
        val napkin = poly(52f, 300f, 140f, 292f, 160f, 330f, 60f, 338f)
        b.body(napkin, 0xFFF4F0E6.toInt(), .25f, 20f, Fade(0f, 292f, 0f, 336f, 40, 200), outline = .7f, washAlpha = 255)
        b.c.save(); b.c.rotate(-8f, 100f, 312f)
        silver(b, Path().apply { addRoundRect(66f, 306f, 120f, 310f, 2f, 2f, Path.Direction.CW) }, 66f, 120f)
        silver(b, Path().apply {
            moveTo(118f, 304f); lineTo(140f, 302f); lineTo(140f, 314f); lineTo(118f, 312f); close()
        }, 118f, 140f)
        for (k in 0 until 3) b.line(126f, 305.5f + k * 3f, 141f, 305.5f + k * 3f, .9f, 0xFF2A2420.toInt())
        b.c.restore()
        b.c.save(); b.c.rotate(-12f, 100f, 326f)
        silver(b, Path().apply { addRoundRect(64f, 324f, 116f, 328f, 2f, 2f, Path.Direction.CW) }, 64f, 116f)
        silver(b, ellipse(126f, 326f, 12f, 6f), 114f, 138f)
        b.c.restore()
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(GLOW, RectF(-90f, -90f, 90f, 90f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 90f,
                intArrayOf(0x99FFE2A0.toInt(), 0x44FFB860, 0x18FF9A40, 0x00FF9A40), floatArrayOf(0f, .2f, .55f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 90f, b.p)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val flame = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val flicker = .85f + .1f * sin(t * 9f) * sin(t * 3.7f) + .05f * sin(t * 23f)
        c.save(); c.translate(CX, 150f); c.scale(1.6f, 1.4f)
        s.draw(c, GLOW, alpha = (255 * flicker).toInt().coerceAtMost(255))
        c.restore()
        for (k in CANDLES.indices step 3) {
            val i = k / 3
            drawFlame(c, CANDLES[k], CANDLES[k + 1] - 4f, t + i * 1.3f)
        }
        // Les reflets des flammes sur l'argent : des éclats qui vacillent avec elles.
        for (g in GLINTS.indices step 2) {
            val i = g / 2
            val pulse = .5f + .5f * sin(t * (4f + i * .7f) + i * 2f)
            val a = (pulse * flicker * 255).toInt()
            val r = 2f + 3.5f * pulse
            val x = GLINTS[g]; val y = GLINTS[g + 1]
            ink.color = withAlpha(0xFFFFFFFF.toInt(), a); ink.strokeWidth = .8f
            c.drawLine(x - r, y, x + r, y, ink); c.drawLine(x, y - r, x, y + r, ink)
            fill.color = withAlpha(0xFFFFF6E0.toInt(), a)
            c.drawCircle(x, y, 1.1f, fill)
        }
    }

    private fun drawFlame(c: Canvas, x: Float, y: Float, t: Float) {
        val h = 15f + 2f * sin(t * 11f) + 1.5f * sin(t * 17f)
        val w = 3.6f
        val sway = 1.4f * sin(t * 5.3f) + .8f * sin(t * 12.7f)
        for (layer in 0 until 3) {
            val k = 1f - layer * .3f
            flame.reset()
            flame.moveTo(x + sway * k, y - h * k)
            flame.cubicTo(x + w * 1.2f * k, y - h * .45f * k, x + w * k, y + 1f, x, y + 2f)
            flame.cubicTo(x - w * k, y + 1f, x - w * 1.2f * k, y - h * .45f * k, x + sway * k, y - h * k)
            flame.close()
            fill.color = when (layer) {
                0 -> withAlpha(0xFFFF9A3A.toInt(), 200)
                1 -> withAlpha(0xFFFFD866.toInt(), 235)
                else -> withAlpha(0xFFFFFAE8.toInt(), 255)
            }
            c.drawPath(flame, fill)
        }
        fill.color = withAlpha(0xFF5A7AE0.toInt(), 120)
        c.drawOval(x - 1.8f, y - 2f, x + 1.8f, y + 1.6f, fill)
    }

    private companion object {
        const val CX = 150f
        const val GLOW = 1
        /** Les bougies : x, haut, bas. */
        val CANDLES = floatArrayOf(110f, 146f, 181f, 150f, 124f, 161f, 190f, 146f, 181f)
        /** Les éclats sur le métal. */
        val GLINTS = floatArrayOf(138f, 284f, 152f, 222f, 236f, 254f, 270f, 246f, 214f, 238f, 130f, 304f, 120f, 322f, 248f, 236f)
    }
}
