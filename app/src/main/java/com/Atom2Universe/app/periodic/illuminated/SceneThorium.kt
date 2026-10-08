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

/**
 * Thorium — la lampe tempête. Le manchon des lampes à gaz, imprégné d'oxyde de thorium, brille
 * d'une lumière blanche bien plus vive que la flamme seule. La nuit, au camping, la lampe posée sur
 * une souche éclaire la tente et les arbres ; son manchon palpite, des papillons de nuit tournent.
 */
internal class SceneThorium : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_gas_lantern
    override val noteRes = R.string.card_note_gas_lantern
    override val explainRes = R.string.card_explain_gas_lantern

    override fun engrave(b: Burin) {
        // Le sol de la clairière, les sapins en ombres, la tente.
        val ground = Path().apply { moveTo(40f, 236f); quadTo(180f, 226f, 320f, 238f); lineTo(320f, 340f); lineTo(40f, 340f); close() }
        b.body(ground, 0xFF1E2A1A.toInt(), .5f, 10f, outline = 0f, washAlpha = 255)
        for ((x, h) in listOf(262f to 120f, 296f to 150f, 236f to 90f)) {
            b.body(Path().apply { moveTo(x, 236f - h); lineTo(x + 24f, 238f); lineTo(x - 24f, 238f); close() }, 0xFF0E1A12.toInt(), .6f, 80f, outline = .6f, washAlpha = 255)
        }
        b.body(poly(50f, 252f, 106f, 166f, 158f, 252f), 0xFF3A5A3A.toInt(), .45f, 60f, outline = .9f, washAlpha = 255)
        b.wash(poly(96f, 252f, 106f, 196f, 118f, 252f), 0xFF0A0E0A.toInt(), 230)
        b.line(106f, 166f, 106f, 158f, 1.4f)
        // La souche qui sert de table.
        b.body(rect(130f, 270f, 230f, 340f), 0xFF4A3424.toInt(), .45f, 90f, outline = .9f, washAlpha = 255)
        b.body(ellipse(180f, 270f, 50f, 10f), 0xFF8A6A48.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        for (r in floatArrayOf(12f, 24f, 36f)) b.c.drawOval(180f - r, 270f - r * .2f, 180f + r, 270f + r * .2f, b.pen(withAlpha(Ink.BROWN, 160), .6f))
        // La lampe : réservoir émaillé, verre, chapeau à évents, anse.
        val tank = Path().apply { addRoundRect(156f, 240f, 204f, 268f, 8f, 8f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(156f, 0f, 204f, 0f,
            intArrayOf(0xFF6A1A14.toInt(), 0xFFC8402E.toInt(), 0xFF4A100C.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(tank, b.p)
        b.stroke(tank, .9f)
        b.body(ellipse(206f, 252f, 4f, 4f), 0xFFC8CCD2.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
        b.body(rect(162f, 232f, 198f, 240f), 0xFF8A8E94.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(Path().apply { moveTo(160f, 160f); quadTo(180f, 140f, 200f, 160f); lineTo(196f, 168f); lineTo(164f, 168f); close() }, 0xFF8A8E94.toInt(), .35f, 0f, outline = .8f, washAlpha = 255)
        for (k in 0 until 4) b.c.drawCircle(168f + k * 8f, 160f, 1.4f, b.pen(0xFF1A1A1E.toInt()))
        b.c.drawArc(RectF(150f, 112f, 210f, 172f), 200f, 140f, false, b.pen(0xFF8A8E94.toInt(), 1.6f))
        for (x in floatArrayOf(164f, 196f)) b.line(x, 168f, x, 232f, 1.2f, 0xFF6A6E74.toInt())
    }

    // ───────────── animation ─────────────

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(MX, MY, 170f, intArrayOf(0x90FFF0C8.toInt(), 0x40FFD890, 0x00FFC870),
            floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 1.6f }
    private val mantle = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val pulse = .9f + .06f * sin(t * 3.1f) + .04f * sin(t * 11f)
        // La lumière de la lampe sur la clairière.
        halo.alpha = (255 * pulse).toInt()
        c.drawCircle(MX, MY, 170f, halo)
        // Le verre, éclairé de l'intérieur.
        fill.color = withAlpha(0xFFFFF4D8.toInt(), (60 * pulse).toInt())
        c.drawRoundRect(164f, 168f, 196f, 232f, 10f, 10f, fill)
        // Le manchon : une petite chaussette de cendre qui brille à blanc.
        mantle.reset()
        mantle.moveTo(MX - 6f, MY - 12f); mantle.quadTo(MX - 10f, MY + 6f, MX, MY + 14f); mantle.quadTo(MX + 10f, MY + 6f, MX + 6f, MY - 12f); mantle.close()
        fill.color = withAlpha(0xFFFFE8B0.toInt(), (120 * pulse).toInt()); c.drawCircle(MX, MY + 2f, 18f, fill)
        fill.color = withAlpha(0xFFFFFDF4.toInt(), (235 * pulse).toInt()); c.drawPath(mantle, fill)
        // Les papillons de nuit tournent autour.
        for (k in 0 until 4) {
            val a = t * (1.1f + .35f * k) + k * 1.7f
            val r = 34f + 14f * k + 6f * sin(t * 2.3f + k)
            val x = MX + r * cos(a)
            val y = MY - 6f + r * .55f * sin(a) + 8f * sin(t * 3.7f + k)
            val flap = 4f * sin(t * 30f + k)
            val near = .5f + .5f * sin(a)
            wing.color = withAlpha(0xFFE8DCC0.toInt(), (140 + 100 * near).toInt())
            c.drawLine(x, y, x - 4f, y - flap, wing); c.drawLine(x, y, x + 4f, y - flap, wing)
        }
    }

    private companion object {
        const val MX = 180f
        const val MY = 196f
    }
}
