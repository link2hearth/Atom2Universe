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
 * Brome — le spa sous la neige. Le brome désinfecte l'eau des spas : contrairement au chlore, il
 * reste efficace dans l'eau chaude. Une nuit d'hiver en montagne, un bain nordique en bois fume
 * sur la terrasse d'un chalet ; l'eau éclairée bouillonne, la vapeur monte, la neige tombe.
 */
internal class SceneBromine : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_hot_tub
    override val noteRes = R.string.card_note_hot_tub
    override val explainRes = R.string.card_explain_hot_tub

    private val water = ellipse(TX, WATER_Y, 84f, 16f)

    override fun engrave(b: Burin) {
        // Les sommets enneigés au loin, la forêt de sapins.
        val peaks = Path().apply {
            moveTo(40f, 170f); lineTo(80f, 128f); lineTo(104f, 146f); lineTo(150f, 100f); lineTo(196f, 150f)
            lineTo(228f, 126f); lineTo(270f, 160f); lineTo(300f, 136f); lineTo(320f, 150f); lineTo(320f, 200f); lineTo(40f, 200f); close()
        }
        b.body(peaks, 0xFF8A98B8.toInt(), .35f, 120f, Fade(0f, 110f, 0f, 196f, 20, 200), outline = .7f, washAlpha = 255)
        b.fill(poly(136f, 114f, 150f, 100f, 166f, 116f, 158f, 112f, 150f, 120f, 144f, 112f), 0xFFF0F4FA.toInt())
        b.fill(poly(70f, 138f, 80f, 128f, 90f, 138f, 84f, 136f, 78f, 140f), 0xFFF0F4FA.toInt())
        b.fill(poly(218f, 134f, 228f, 126f, 240f, 136f, 232f, 134f, 226f, 138f), 0xFFF0F4FA.toInt())
        // Le champ de neige entre la forêt et la terrasse.
        b.body(rect(40f, 196f, 320f, DECK), 0xFFDCE4F0.toInt(), .2f, 0f, Fade(0f, 200f, 0f, DECK, 10, 120), outline = 0f, washAlpha = 255)
        for (k in 0 until 16) {
            val x = 40f + k * 18f + 6f * hash(k, 1)
            val h = 34f + 22f * hash(k, 2)
            fir(b, x, 212f, h)
        }
        // Le mur du chalet à droite, sa fenêtre chaude.
        val chalet = rect(278f, 120f, 320f, DECK)
        b.body(chalet, 0xFF5A3A24.toInt(), .45f, 0f, outline = .9f, washAlpha = 255)
        for (y in floatArrayOf(132f, 146f, 160f, 174f, 188f, 202f, 216f, 230f)) b.line(278f, y, 320f, y, .8f, withAlpha(Ink.SEPIA, 200))
        b.glow(300f, 176f, 50f, 0xFFFFC870.toInt(), 120)
        b.body(rect(288f, 156f, 320f, 196f), 0xFFFFD890.toInt(), 0f, outline = 1f, washAlpha = 255)
        b.line(304f, 156f, 304f, 196f, 2f, 0xFF5A3A24.toInt())
        b.line(288f, 176f, 320f, 176f, 2f, 0xFF5A3A24.toInt())
        // La terrasse enneigée.
        val deck = rect(40f, DECK, 320f, 340f)
        b.wash(deck, 0xFF6A4A30.toInt(), 255)
        var y = DECK + 6f
        while (y < 340f) { b.line(40f, y, 320f, y, .7f, Ink.SEPIA); y += 7f + (y - DECK) * .12f }
        b.washGradient(deck, 0xFFE8EEF6.toInt(), 0f, DECK, 230, 0f, 300f, 60)
        // La lanterne sur son poteau, à gauche.
        b.body(rect(66f, 170f, 70f, DECK), 0xFF3A2A1C.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        b.body(poly(60f, 158f, 76f, 158f, 74f, 174f, 62f, 174f), 0xFFFFD890.toInt(), 0f, outline = .9f, washAlpha = 255)
        b.fill(poly(58f, 158f, 68f, 150f, 78f, 158f), 0xFF2A2018.toInt())
        // Le bain : douelles de bois, cerclages, rebord ; l'eau éclairée de l'intérieur.
        tub(b)
    }

    private fun fir(b: Burin, x: Float, base: Float, h: Float) {
        val tree = poly(x, base - h, x + h * .32f, base, x - h * .32f, base)
        b.body(tree, 0xFF1A2A24.toInt(), .5f, 60f, outline = .6f, washAlpha = 255)
        b.fill(poly(x, base - h, x + h * .12f, base - h * .62f, x, base - h * .7f, x - h * .12f, base - h * .62f), withAlpha(0xFFE8EEF6.toInt(), 200))
    }

    private fun tub(b: Burin) {
        val side = Path().apply {
            addOval(TX - 92f, RIM_Y - 20f, TX + 92f, RIM_Y + 20f, Path.Direction.CW)
            op(rect(TX - 92f, RIM_Y, TX + 92f, BOTTOM), Path.Op.UNION)
            op(ellipse(TX, BOTTOM, 92f, 20f), Path.Op.UNION)
        }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(TX - 92f, 0f, TX + 92f, 0f,
            intArrayOf(0xFF6A4228.toInt(), 0xFFA8743E.toInt(), 0xFF8A5A30.toInt(), 0xFF4A2A18.toInt()), floatArrayOf(0f, .3f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(side, b.p)
        b.c.save(); b.c.clipPath(side)
        for (k in -8..8) {
            val x = TX + 92f * sin(k / 9f * 1.45f)
            b.line(x, RIM_Y, x, BOTTOM + 20f, .8f, withAlpha(Ink.SEPIA, 200))
        }
        for (yb in floatArrayOf(RIM_Y + 22f, BOTTOM - 14f)) {
            b.c.drawArc(TX - 92f, yb - 20f, TX + 92f, yb + 20f, 0f, 180f, false, b.pen(0xFF2A2C30.toInt(), 3.4f))
            b.c.drawArc(TX - 92f, yb - 21.4f, TX + 92f, yb + 18.6f, 20f, 140f, false, b.pen(withAlpha(0xFFB8BEC4.toInt(), 150), .8f))
        }
        b.c.restore()
        b.stroke(side, 1f)
        // Le rebord, la neige posée dessus, puis l'eau.
        b.body(ellipse(TX, RIM_Y, 92f, 20f), 0xFF8A5A30.toInt(), .2f, 0f, outline = 1f, washAlpha = 255)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, WATER_Y - 16f, 0f, WATER_Y + 16f,
            intArrayOf(0xFF7AE8E0.toInt(), 0xFF2AA8C0.toInt(), 0xFF1A6A98.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(water, b.p)
        b.glow(TX, WATER_Y + 4f, 50f, 0xFFC8FFF8.toInt(), 150)
        b.stroke(water, .8f, withAlpha(Ink.SEPIA, 200))
        for (k in 0 until 4) {
            val a = 3.6f + k * .5f
            b.fill(ellipse(TX + 90f * cos(a), RIM_Y + 19f * sin(a) - 1f, 9f, 2.4f), 0xFFF4F8FC.toInt())
        }
        // La lueur de l'eau sur la neige devant le bain.
        b.glow(TX, BOTTOM + 24f, 90f, 0xFF7AE8E0.toInt(), 40)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La lanterne vacille.
        val flame = .8f + .2f * sin(t * 9f) * sin(t * 3.7f)
        fill.color = withAlpha(0xFFFFC870.toInt(), (80 * flame).toInt())
        c.drawCircle(68f, 166f, 22f, fill)
        // Le bouillonnement : des bulles qui naissent, gonflent et crèvent, plus serrées sur les jets.
        c.save(); c.clipPath(water)
        for (k in 0 until BUBBLES) {
            val period = .9f + .6f * hash(k, 1)
            val cycle = ((t + hash(k, 2) * 3f) / period).toInt()
            val life = ((t + hash(k, 2) * 3f) % period) / period
            val jet = k % 3
            val jx = TX - 50f + jet * 50f
            val x = jx + (hash(cycle, k) - .5f) * 52f
            val y = WATER_Y - 10f + hash(cycle, k + 99) * 20f
            val r = 1f + 3.4f * life
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (220 * (1f - life * life)).toInt()); ink.strokeWidth = .8f
            c.drawCircle(x, y, r, ink)
        }
        // L'écume des trois jets, qui tourne.
        for (jet in 0 until 3) {
            val jx = TX - 50f + jet * 50f
            for (k in 0 until 5) {
                val a = t * (2f + jet * .4f) + k * 1.26f
                fill.color = withAlpha(0xFFF4FFFF.toInt(), 120)
                c.drawCircle(jx + 9f * cos(a), WATER_Y + 3f * sin(a), 3f + 1.4f * sin(a * 2f + k), fill)
            }
        }
        c.restore()
        // La vapeur : de grandes volutes qui montent, dérivent et s'effacent.
        for (k in 0 until STEAM) {
            val life = ((t * .16f + k / STEAM.toFloat()) % 1f)
            val x0 = TX - 60f + 120f * hash(k, 3)
            val x = x0 + 24f * life + 8f * sin(life * 4f + k)
            val y = WATER_Y - 6f - life * 120f
            val r = 6f + life * 22f
            fill.color = withAlpha(0xFFE8F0F8.toInt(), (60 * (1f - life) * (life * 4f).coerceAtMost(1f)).toInt())
            c.drawCircle(x, y, r, fill)
        }
        // La neige qui tombe, en biais, chaque flocon à sa vitesse.
        for (k in 0 until FLAKES) {
            val speed = 16f + 18f * hash(k, 4)
            val y = 50f + ((hash(k, 5) * 290f + t * speed) % 290f)
            val x = 40f + ((hash(k, 6) * 280f + t * 6f + 6f * sin(t * .8f + k)) % 280f)
            val r = .8f + 1.2f * hash(k, 7)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), 220)
            c.drawCircle(x, y, r, fill)
        }
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val DECK = 244f
        const val TX = 172f
        const val RIM_Y = 220f
        const val WATER_Y = 223f
        const val BOTTOM = 296f
        const val BUBBLES = 36
        const val STEAM = 9
        const val FLAKES = 46
    }
}
