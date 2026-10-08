package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Néon — l'enseigne lumineuse d'un café, la nuit, sous la pluie. Le vrai néon brille rouge
 * orangé (les autres couleurs viennent d'autres gaz) : une tasse en tubes de verre, sa vapeur
 * qui s'allume tube après tube, un tube qui grésille, et le reflet sur les pavés mouillés.
 */
internal class SceneNeon : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_neon_sign
    override val noteRes = R.string.card_note_neon_sign
    override val explainRes = R.string.card_explain_neon_sign

    override fun engrave(b: Burin) {
        facade(b)
        upperWindows(b)
        shopfront(b)
        street(b)
        // Les tubes éteints : du verre pâle, visible quand le gaz ne brille pas.
        for (tube in tubes) {
            val path = Path().apply {
                moveTo(tube[0], tube[1]); for (i in 2 until tube.size step 2) lineTo(tube[i], tube[i + 1])
            }
            b.stroke(path, 2.4f, withAlpha(0xFF6E5A5A.toInt(), 220))
            b.stroke(path, 1f, withAlpha(0xFFE8D8D0.toInt(), 160))
        }
        // Les pattes de fixation et le transformateur.
        for ((x, y) in listOf(150f to 140f, 210f to 140f, 146f to 192f, 214f to 192f)) b.line(x, y, x + (if (x < 180f) -5f else 5f), y - 4f, .8f, 0xFF9A9AA6.toInt())
        b.body(rect(172f, 200f, 188f, 206f), 0xFF5A5A66.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
    }

    private fun facade(b: Burin) {
        val wall = rect(40f, 84f, 320f, 270f)
        b.body(wall, 0xFF3C4560.toInt(), 0f, outline = 0f, washAlpha = 255)
        // Les assises de pierre, gravées en bandes, et leurs joints décalés.
        b.hatchRect(android.graphics.RectF(40f, 84f, 320f, 270f), 0f, 1.6f, .5f, 0xFF1A2032.toInt())
        val joint = b.pen(withAlpha(0xFF8A94AE.toInt(), 120), .5f)
        var y = 92f; var row = 0
        while (y < 270f) {
            b.c.drawLine(40f, y, 320f, y, joint)
            var x = 40f + (if (row % 2 == 0) 0f else 14f)
            while (x < 320f) { b.c.drawLine(x, y - 9f, x, y, joint); x += 28f }
            y += 9f; row++
        }
        // La corniche et le toit, sur le ciel.
        b.body(rect(40f, 78f, 320f, 88f), 0xFF5A6480.toInt(), .4f, 0f, outline = .7f, washAlpha = 255)
        b.line(40f, 88f, 320f, 88f, 1f)
        for (x in floatArrayOf(84f, 262f)) b.body(rect(x, 62f, x + 14f, 78f), 0xFF4A5470.toInt(), .45f, 90f, outline = .7f, washAlpha = 255)
    }

    private fun upperWindows(b: Burin) {
        for ((i, x) in floatArrayOf(66f, 258f).withIndex()) {
            val pane = rect(x, 104f, x + 36f, 156f)
            if (i == 1) {
                // Une fenêtre éclairée : lumière chaude et rideau.
                b.wash(pane, 0xFFF2C46A.toInt(), 240)
                b.washGradient(pane, 0xFFB86A2A.toInt(), x, 104f, 0, x, 156f, 140)
                b.hatch(rect(x + 22f, 104f, x + 36f, 156f), 90f, 1.6f, .45f, withAlpha(0xFF8A4A1A.toInt(), 180))
            } else {
                b.wash(pane, 0xFF1A2236.toInt(), 255)
                b.hatch(pane, 70f, 2.2f, .4f, withAlpha(0xFF8A94AE.toInt(), 90))
            }
            b.line(x + 18f, 104f, x + 18f, 156f, 1f); b.line(x, 130f, x + 36f, 130f, 1f)
            b.stroke(pane, 1.2f)
            // Les volets.
            for (sx in floatArrayOf(x - 12f, x + 36f)) {
                val shutter = rect(sx, 102f, sx + 12f, 158f)
                b.body(shutter, 0xFF2F4A44.toInt(), .5f, 0f, outline = .7f, washAlpha = 255)
            }
            b.body(rect(x - 4f, 156f, x + 40f, 161f), 0xFF5A6480.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        }
    }

    private fun shopfront(b: Burin) {
        // Le store rayé au-dessus de la vitrine, festonné.
        val awning = Path().apply {
            moveTo(56f, 210f); lineTo(304f, 210f); lineTo(312f, 226f)
            for (i in 0 until 12) {
                val x0 = 312f - i * 22f
                quadTo(x0 - 11f, 234f, x0 - 22f, 226f)
            }
            lineTo(56f, 210f); close()
        }
        b.wash(awning, 0xFFEDE3D0.toInt(), 255)
        b.c.save(); b.c.clipPath(awning)
        for (i in 0 until 26) if (i % 2 == 0) b.c.drawRect(48f + i * 11f, 206f, 59f + i * 11f, 236f, b.pen(0xFF8C2A24.toInt()))
        b.c.restore()
        b.hatch(awning, 0f, 1.5f, .45f, Ink.SEPIA, Fade(0f, 236f, 0f, 210f, 230, 60))
        b.stroke(awning, .8f)
        // La vitrine : la salle chaude du café, ses tables et ses chaises.
        val glass = rect(62f, 230f, 298f, 270f)
        b.wash(glass, 0xFFE9B864.toInt(), 255)
        b.washGradient(glass, 0xFF9A5A22.toInt(), 0f, 230f, 160, 0f, 270f, 40)
        val ink = withAlpha(0xFF3A2414.toInt(), 230)
        val rnd = Random(3)
        for (k in 0 until 6) {
            val x = 80f + k * 38f + rnd.nextFloat() * 6f
            b.c.drawOval(x - 9f, 254f, x + 9f, 257f, b.pen(ink))
            b.line(x, 257f, x, 268f, 1.4f, ink)
            b.line(x - 5f, 268f, x + 5f, 268f, 1f, ink)
            for (s in floatArrayOf(-1f, 1f)) {
                val cx = x + s * 13f
                b.line(cx, 250f, cx, 268f, 1f, ink)
                b.line(cx, 259f, cx - s * 6f, 259f, 1f, ink)
                b.line(cx - s * 6f, 259f, cx - s * 6f, 268f, 1f, ink)
            }
        }
        for (x in floatArrayOf(120f, 180f, 240f)) {
            b.line(x, 230f, x, 240f, .5f, ink)
            b.c.drawArc(x - 6f, 238f, x + 6f, 246f, 180f, 180f, true, b.pen(withAlpha(0xFF6A3A14.toInt(), 230)))
        }
        b.hatch(glass, 75f, 3.6f, .4f, withAlpha(0xFFFFFFFF.toInt(), 70))
        for (x in floatArrayOf(62f, 121f, 180f, 239f, 298f)) b.line(x, 228f, x, 270f, 2.2f, 0xFF2A1A12.toInt())
        b.stroke(glass, 1.4f, 0xFF2A1A12.toInt())
    }

    private fun street(b: Burin) {
        // Le trottoir, puis la chaussée pavée et mouillée.
        val curb = rect(40f, 270f, 320f, 286f)
        b.body(curb, 0xFF5E6478.toInt(), .45f, 0f, Fade(0f, 286f, 0f, 270f, 230, 60), outline = 0f, washAlpha = 255)
        b.line(40f, 286f, 320f, 286f, 1.2f)
        val road = rect(40f, 286f, 320f, 346f)
        b.body(road, 0xFF2A3044.toInt(), 0f, outline = 0f, washAlpha = 255)
        val rnd = Random(8)
        var y = 290f; var row = 0
        while (y < 346f) {
            val h = 5f + row * 1.2f
            var x = 40f - rnd.nextFloat() * 8f
            while (x < 320f) {
                val w = h * 1.5f
                b.c.drawOval(x + .6f, y, x + w - .6f, y + h - .8f, b.pen(withAlpha(0xFF6A7290.toInt(), 150), .5f))
                x += w
            }
            y += h; row++
        }
        b.hatchRect(android.graphics.RectF(40f, 286f, 320f, 346f), 0f, 2f, .4f, 0xFF12182A.toInt(), Fade(0f, 346f, 0f, 290f, 200, 40))
    }

    // ───────────── les tubes ─────────────

    /** Chaque tube est une ligne brisée : la tasse, l'anse, la soucoupe, puis trois volutes. */
    private val tubes: List<FloatArray> = listOf(
        line(13) { u -> (148f + 64f * u) to 140f },
        line(28) { u ->
            val a = PI.toFloat() * u
            (180f - 30f * cos(a)) to (140f + 44f * sin(a).coerceAtLeast(0f).pow(.7f))
        },
        line(16) { u ->
            val a = (-100f + 200f * u) * PI.toFloat() / 180f
            (211f + 12f * cos(a)) to (160f + 11f * sin(a))
        },
        line(20) { u -> (138f + 84f * u) to (192f + 4f * sin(PI.toFloat() * u)) },
        line(14) { u -> (166f + 4f * sin(u * 2 * PI.toFloat())) to (132f - 34f * u) },
        line(14) { u -> (180f + 4f * sin(u * 2 * PI.toFloat() + 2f)) to (130f - 38f * u) },
        line(14) { u -> (194f + 4f * sin(u * 2 * PI.toFloat() + 4f)) to (132f - 34f * u) }
    )

    private fun line(n: Int, f: (Float) -> Pair<Float, Float>) = FloatArray((n + 1) * 2).also { out ->
        for (i in 0..n) { val (x, y) = f(i / n.toFloat()); out[i * 2] = x; out[i * 2 + 1] = y }
    }

    /** Les mêmes tubes en segments, prêts pour `drawLines`. */
    private val segments: List<FloatArray> = tubes.map { t ->
        FloatArray((t.size / 2 - 1) * 4).also { s ->
            for (i in 0 until t.size / 2 - 1) {
                s[i * 4] = t[i * 2]; s[i * 4 + 1] = t[i * 2 + 1]; s[i * 4 + 2] = t[i * 2 + 2]; s[i * 4 + 3] = t[i * 2 + 3]
            }
        }
    }

    // ───────────── animation ─────────────

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFFFF3A1A.toInt() }
    private val mid = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFFFF5A2E.toInt() }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFFFFE6D6.toInt() }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, 0x99FF4A22.toInt(), 0x00FF4A22, Shader.TileMode.CLAMP)
    }
    private val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val drops = FloatArray(RAIN * 3).also { d ->
        val rnd = Random(5)
        for (i in 0 until RAIN) { d[i * 3] = rnd.nextFloat(); d[i * 3 + 1] = rnd.nextFloat(); d[i * 3 + 2] = .7f + rnd.nextFloat() * .6f }
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Le grésillement : de temps en temps, l'anse hésite avant de se rallumer.
        val f = t % 6.5f
        val handle = if (f < .7f) (if (sin(f * 47f) + sin(f * 83f) > .3f) .25f else 1f) else 1f
        val hum = .93f + .07f * sin(t * 31f) * sin(t * 7f)

        c.save(); c.translate(180f, 152f); c.scale(120f, 96f)
        halo.alpha = (255 * hum).toInt()
        c.drawCircle(0f, 0f, 1f, halo)
        c.restore()

        for ((k, seg) in segments.withIndex()) {
            val level = when (k) {
                2 -> handle
                in 4..6 -> if (((t * .6f - (k - 4) * .25f) % 1f + 1f) % 1f < .7f) 1f else 0f
                else -> 1f
            } * hum
            if (level <= .05f) continue
            glow.alpha = (55 * level).toInt(); glow.strokeWidth = 9f
            c.drawLines(seg, glow)
            mid.alpha = (220 * level).toInt(); mid.strokeWidth = 3.4f
            c.drawLines(seg, mid)
            core.alpha = (255 * level).toInt(); core.strokeWidth = 1.2f
            c.drawLines(seg, core)
        }

        // Le reflet sur les pavés mouillés : de petits traits que l'eau brise et fait trembler.
        c.save(); c.clipRect(40f, 287f, 320f, 346f)
        streak.strokeWidth = 1.5f
        for (j in 0 until 9) {
            val y = 292f + j * 5.5f
            val fade = 1f - j / 9f
            for (k in 0 until 14) {
                val x = 144f + k * 5.2f + 2.2f * sin(t * 2.1f + j * 1.3f + k)
                val a = (.35f + .65f * (.5f + .5f * sin(t * 3.4f + j * 2.1f + k * 1.7f))) * fade * hum
                val len = 1.6f + 1.4f * sin(k * 2.3f + j)
                streak.color = withAlpha(0xFFFF5A2E.toInt(), (130 * a).toInt())
                c.drawLine(x - len, y, x + len, y, streak)
            }
        }
        for (j in 0 until 5) {
            val y = 292f + j * 5f
            for (k in 0 until 18) {
                val x = 66f + k * 13f + 1.6f * sin(t * 1.7f + j + k)
                streak.color = withAlpha(0xFFF2C46A.toInt(), (60 * (1f - j / 5f)).toInt())
                c.drawLine(x - 2f, y, x + 2f, y, streak)
            }
        }
        c.restore()

        // La pluie, et ses ronds dans les flaques.
        streak.strokeWidth = .6f
        for (i in 0 until RAIN) {
            val speed = drops[i * 3 + 2]
            val y = 60f + ((drops[i * 3 + 1] + t * .9f * speed) % 1f) * 290f
            val x = 46f + ((drops[i * 3] + y * .0006f) % 1f) * 268f
            streak.color = withAlpha(0xFFDDE6F6.toInt(), 120)
            c.drawLine(x, y, x - 1.6f, y + 8f, streak)
        }
        for (i in 0 until 5) {
            val p = (t * .8f + i * .37f) % 1f
            val x = 60f + ((i * 97 + (t * .8f + i * .37f).toInt() * 53) % 240)
            val y = 300f + (i * 13 % 40)
            val r = 2f + 9f * p
            streak.color = withAlpha(0xFFDDE6F6.toInt(), (140 * (1f - p)).toInt())
            c.drawOval(x - r, y - r * .3f, x + r, y + r * .3f, streak)
        }
    }

    private companion object {
        const val RAIN = 46
    }
}
