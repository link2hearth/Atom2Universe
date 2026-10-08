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
 * Strontium — la fusée rouge. Ce sont les sels de strontium qui donnent leur rouge aux fusées de
 * signalisation et aux feux d'artifice. La nuit, en mer, un voilier tire une fusée à parachute :
 * elle monte en sifflant, s'ouvre, puis descend lentement en éclairant la mer de rouge ; quand
 * elle s'éteint, une autre part.
 */
internal class SceneStrontium : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_red_flare
    override val noteRes = R.string.card_note_red_flare
    override val explainRes = R.string.card_explain_red_flare

    override fun engrave(b: Burin) {
        // La côte au loin, un phare minuscule, la mer.
        val coast = Path().apply {
            moveTo(40f, SEA); lineTo(40f, 190f); cubicTo(70f, 184f, 100f, 192f, 130f, 194f); lineTo(150f, SEA); close()
        }
        b.body(coast, 0xFF1A1E2A.toInt(), .4f, 0f, outline = .6f, washAlpha = 255)
        b.line(66f, 186f, 66f, 176f, 2f, 0xFF3A3E4A.toInt())
        val sea = rect(40f, SEA, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, SEA, 0f, 330f,
            intArrayOf(0xFF1A2A44.toInt(), 0xFF0A1424.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(sea, b.p)
        var y = SEA + 4f
        var k = 0
        while (y < 330f) {
            val gap = 4f + (y - SEA) * .1f
            var x = 40f + (k % 3) * 9f
            while (x < 320f) { b.line(x, y, x + 10f + (y - SEA) * .1f, y, .6f, withAlpha(0xFF6A8AB8.toInt(), 110)); x += 26f + (y - SEA) * .2f }
            y += gap; k++
        }
        b.line(40f, SEA, 320f, SEA, .8f, withAlpha(0xFF8AA8D8.toInt(), 160))
    }

    override fun sprites(): List<Sprite> = listOf(
        // Le voilier : coque, mât, grand-voile et foc ; posé sur l'eau, il tangue.
        Sprite(BOAT, RectF(-46f, -96f, 50f, 14f)) { b ->
            val hull = Path().apply { moveTo(-44f, -6f); lineTo(46f, -8f); cubicTo(40f, 6f, 20f, 10f, 0f, 10f); cubicTo(-24f, 10f, -38f, 6f, -44f, -6f); close() }
            b.body(hull, 0xFFE8E4DC.toInt(), .35f, 0f, Fade(0f, 10f, 0f, -6f, 220, 40), outline = 1f, washAlpha = 255)
            b.line(-44f, -4f, 46f, -6f, 1.4f, 0xFF2A4A7A.toInt())
            b.body(rect(-14f, -14f, 12f, -6f), 0xFFD8D4CC.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.line(-2f, -6f, -2f, -94f, 1.8f, 0xFF8A8C90.toInt())
            val main = Path().apply { moveTo(-4f, -90f); quadTo(-24f, -50f, -40f, -12f); lineTo(-4f, -12f); close() }
            b.body(main, 0xFFF4F0E8.toInt(), .3f, 90f, Fade(-40f, 0f, -4f, 0f, 200, 30), outline = .9f, washAlpha = 255)
            val jib = Path().apply { moveTo(0f, -88f); lineTo(44f, -8f); lineTo(0f, -12f); close() }
            b.body(jib, 0xFFF4F0E8.toInt(), .25f, 90f, Fade(44f, 0f, 0f, 0f, 160, 20), outline = .9f, washAlpha = 255)
            b.c.drawCircle(-2f, -94f, 1.6f, b.pen(0xFFFFFFFF.toInt()))
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val chute = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val ph = t % CYCLE
        val launch = (t / CYCLE).toInt()
        val drift = 30f * (hash(launch, 1) - .5f)
        // La fusée : montée d'une seconde, puis descente lente sous parachute en dérivant.
        val fx: Float; val fy: Float; val bright: Float
        when {
            ph < RISE -> {
                val u = ph / RISE
                fx = BOAT_X + (TOP_X + drift - BOAT_X) * u
                fy = BOAT_Y - 90f - (BOAT_Y - 90f - TOP_Y) * (1f - (1f - u) * (1f - u))
                bright = .3f * u
            }
            else -> {
                val u = (ph - RISE) / (CYCLE - RISE)
                fx = TOP_X + drift + 26f * u + 4f * sin(ph * 1.3f)
                fy = TOP_Y + 96f * u
                // Elle s'allume vite, brûle en crépitant, et s'éteint sur la fin.
                bright = ((ph - RISE) / .4f).coerceAtMost(1f) * ((1f - u) / .15f).coerceAtMost(1f) * (.85f + .15f * sin(t * 23f) * sin(t * 7f))
            }
        }
        // La lueur rouge sur tout le ciel et la mer.
        fill.color = withAlpha(0xFFFF2A1A.toInt(), (45 * bright).toInt())
        c.drawRect(40f, 50f, 320f, 340f, fill)
        fill.color = withAlpha(0xFFFF5A3A.toInt(), (90 * bright).toInt())
        c.drawCircle(fx, fy, 70f, fill)
        // Le reflet : une colonne de vaguelettes rouges sous la fusée.
        var y = SEA + 3f
        var k = 0
        while (y < 330f) {
            val spread = 4f + (y - SEA) * .22f
            val wob = 3f * sin(t * 2.2f + k * 1.7f)
            ink.color = withAlpha(0xFFFF6A4A.toInt(), (200 * bright * (1f - (y - SEA) / 160f).coerceAtLeast(.2f)).toInt())
            ink.strokeWidth = 1f + (y - SEA) * .015f
            c.drawLine(fx - spread + wob, y, fx + spread + wob, y, ink)
            y += 4f + (y - SEA) * .08f; k++
        }
        // Le voilier tangue.
        val roll = 2f * sin(t * .9f)
        val heave = 1.6f * sin(t * 1.3f)
        s.draw(c, BOAT, dx = BOAT_X, dy = BOAT_Y + heave, deg = roll)
        // Sur les voiles, la lumière rouge.
        fill.color = withAlpha(0xFFFF4A2A.toInt(), (70 * bright).toInt())
        c.drawCircle(BOAT_X + 10f, BOAT_Y - 40f + heave, 34f, fill)
        if (ph < RISE) {
            // La traînée d'étincelles de la montée.
            for (i in 0 until 8) {
                val back = i * .04f
                val u = ((ph - back) / RISE).coerceAtLeast(0f)
                val x = BOAT_X + (TOP_X + drift - BOAT_X) * u
                val yy = BOAT_Y - 90f - (BOAT_Y - 90f - TOP_Y) * (1f - (1f - u) * (1f - u))
                fill.color = withAlpha(0xFFFFC890.toInt(), 200 - i * 22)
                c.drawCircle(x, yy, 1.6f - i * .12f, fill)
            }
        } else {
            // La fumée qui monte au-dessus, le parachute, la flamme.
            for (i in 0 until 10) {
                val back = (ph - RISE) - i * .35f
                if (back < 0f) break
                val u = back / (CYCLE - RISE)
                val sx = TOP_X + drift + 26f * u + 4f * sin((RISE + back) * 1.3f) + i * 1.6f
                val sy = TOP_Y + 96f * u - 8f
                fill.color = withAlpha(0xFFD8B0A8.toInt(), ((60 - i * 5) * bright).toInt().coerceAtLeast(0))
                c.drawCircle(sx, sy, 3f + i * 1.4f, fill)
            }
            chute.reset()
            chute.moveTo(fx - 9f, fy - 16f); chute.quadTo(fx, fy - 28f, fx + 9f, fy - 16f); chute.close()
            fill.color = 0xFFE8E0D8.toInt(); c.drawPath(chute, fill)
            ink.color = withAlpha(Ink.SEPIA, 200); ink.strokeWidth = .5f
            c.drawLine(fx - 9f, fy - 16f, fx, fy - 2f, ink); c.drawLine(fx + 9f, fy - 16f, fx, fy - 2f, ink)
            fill.color = withAlpha(0xFFFF8A6A.toInt(), (160 * bright).toInt()); c.drawCircle(fx, fy, 7f, fill)
            fill.color = withAlpha(0xFFFFF0E8.toInt(), (255 * bright).toInt()); c.drawCircle(fx, fy, 2.6f, fill)
        }
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val SEA = 200f
        const val BOAT_X = 124f
        const val BOAT_Y = 252f
        const val TOP_X = 220f
        const val TOP_Y = 86f
        const val RISE = 1.1f
        const val CYCLE = 11f
        const val BOAT = 1
    }
}
