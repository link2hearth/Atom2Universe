package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sodium — le sel, dans un marais salant. Le soleil et le vent évaporent l'eau de mer dans
 * les bassins d'argile ; le paludier tire le sel cristallisé avec son long râteau et le monte
 * en tas blancs. Le moulin tourne au loin, l'eau scintille, les mouettes passent.
 */
internal class SceneSodium : EngravedScene() {

    override val nameRes = R.string.card_scene_salt_marsh
    override val noteRes = R.string.card_note_salt_marsh
    override val explainRes = R.string.card_explain_salt_marsh

    private fun sx(x: Float, z: Float) = 180f + x * F / z
    private fun sy(z: Float) = HORIZON + F / z

    override fun engrave(b: Burin) {
        horizon(b)
        marsh(b)
        mounds(b)
        worker(b)
    }

    private fun horizon(b: Burin) {
        // La mer, mince bande au loin, et la côte basse avec son village.
        b.body(rect(40f, HORIZON - 6f, 320f, HORIZON), 0xFF7E9CB4.toInt(), .3f, 0f, outline = .5f, washAlpha = 200)
        val coast = Path().apply {
            moveTo(40f, HORIZON - 4f); cubicTo(70f, HORIZON - 10f, 110f, HORIZON - 8f, 140f, HORIZON - 3f)
            lineTo(140f, HORIZON + 2f); lineTo(40f, HORIZON + 2f); close()
        }
        b.body(coast, 0xFF8EA078.toInt(), .35f, 0f, outline = .5f, washAlpha = 180)
        val rnd = Random(5)
        var x = 58f
        while (x < 124f) {
            val w = 5f + rnd.nextFloat() * 5f; val h = 4f + rnd.nextFloat() * 4f
            b.body(poly(x, HORIZON - 4f, x, HORIZON - 4f - h, x + w / 2f, HORIZON - 7f - h, x + w, HORIZON - 4f - h, x + w, HORIZON - 4f), 0xFFE8DCC4.toInt(), .25f, 90f, outline = .45f, washAlpha = 220)
            x += w + 1f
        }
        b.body(rect(86f, HORIZON - 20f, 90f, HORIZON - 6f), 0xFFE8DCC4.toInt(), .3f, 90f, outline = .5f)
        b.body(poly(85.5f, HORIZON - 20f, 88f, HORIZON - 31f, 90.5f, HORIZON - 20f), 0xFF6A7A86.toInt(), .4f, 60f, outline = .5f)
        // Le moulin, sur la droite ; ses ailes tournent dans l'animation.
        b.body(poly(MILL_X - 5f, HORIZON, MILL_X - 3.5f, MILL_Y + 2f, MILL_X + 3.5f, MILL_Y + 2f, MILL_X + 5f, HORIZON), 0xFFF0E8D8.toInt(), .3f, 90f, Fade(MILL_X + 5f, 0f, MILL_X - 2f, 0f, 230, 0), outline = .6f, washAlpha = 255)
        b.body(poly(MILL_X - 4.5f, MILL_Y + 2.5f, MILL_X, MILL_Y - 3f, MILL_X + 4.5f, MILL_Y + 2.5f), 0xFF5A4A3A.toInt(), .4f, 0f, outline = .6f, washAlpha = 255)
    }

    /** Les bassins en perspective : de l'eau qui renvoie le ciel, séparée par des talus d'argile. */
    private fun marsh(b: Burin) {
        val ground = rect(40f, HORIZON, 320f, 340f)
        b.wash(ground, 0xFFA89A7A.toInt(), 255)
        b.hatch(ground, 0f, 1.8f, .45f, Ink.BROWN, Fade(0f, HORIZON, 0f, 330f, 120, 220))
        val rnd = Random(11)
        for (i in 0 until COLS.size - 1) for (j in 0 until ROWS.size - 1) {
            val z0 = ROWS[j]; val z1 = ROWS[j + 1]
            val x0 = COLS[i] + DYKE; val x1 = COLS[i + 1] - DYKE
            val near = z0 + DYKE * .6f; val far = z1 - DYKE * 1.2f
            val pool = poly(sx(x0, near), sy(near), sx(x1, near), sy(near), sx(x1, far), sy(far), sx(x0, far), sy(far))
            val crust = rnd.nextFloat() < .3f
            b.washGradient(pool, if (crust) 0xFFF4F0E6.toInt() else 0xFFBCD2E6.toInt(), 0f, sy(far), 255, 0f, sy(near), 200)
            if (!crust) b.washGradient(pool, 0xFFF2D2C2.toInt(), 0f, sy(far), 0, 0f, sy(near), 110)
            b.hatch(pool, 0f, 1.2f + 2.4f / z0, .4f, withAlpha(0xFF5A6A80.toInt(), if (crust) 60 else 140))
            if (crust) b.stipple(pool, 60, .6f, withAlpha(0xFF8A8A80.toInt(), 160), i * 7 + j)
            else b.stipple(pool, 25, .7f, 0xFFFFFFFF.toInt(), i * 5 + j)
        }
        // Les talus : des bandes d'argile plus épaisses vers nous.
        for (x in COLS) {
            val zf = ROWS.last(); val zn = ROWS.first()
            val dyke = limb(sx(x, zf), sy(zf), sx(x, zn), sy(zn), 1.6f * DYKE * F / zf, 1.6f * DYKE * F / zn)
            clay(b, dyke, 500, x.toInt() + 20)
        }
        for (z in ROWS) {
            val y = sy(z)
            val w = 1.5f * DYKE * F / z
            val dyke = rect(40f, y - w * .5f, 320f, y + w * .5f)
            clay(b, dyke, (w * 30f).toInt(), z.toInt() + 3)
        }
    }

    /** Un talus d'argile tassée : lavis, pointillé de terre, un peu d'herbe rase. */
    private fun clay(b: Burin, dyke: Path, dots: Int, seed: Int) {
        b.wash(dyke, 0xFF968D68.toInt(), 255)
        b.stipple(dyke, dots, .55f, withAlpha(Ink.BROWN, 210), seed)
        b.stipple(dyke, dots / 3, .7f, withAlpha(0xFF5E7A3A.toInt(), 200), seed + 50)
        b.hatch(dyke, 0f, 2.6f, .35f, withAlpha(Ink.SEPIA, 90))
        b.stroke(dyke, .5f)
    }

    /** Les tas de sel : un grand mulon blanc, un plus loin, et les petits tas du jour. */
    private fun mounds(b: Burin) {
        mound(b, sx(.47f, 2.2f) + 8f, sy(2.2f) + 2f, 30f, 42f)
        mound(b, sx(2.47f, 4.2f), sy(4.2f) + 1f, 16f, 22f)
        mound(b, sx(-1.53f, 3.2f), sy(3.2f) + 1f, 11f, 15f)
        for (x in floatArrayOf(246f, 292f)) mound(b, x, sy(1.2f) + 2f, 6f, 10f)
    }

    private fun mound(b: Burin, x: Float, base: Float, h: Float, half: Float) {
        val pile = Path().apply {
            moveTo(x - half, base)
            cubicTo(x - half * .6f, base - h * .5f, x - half * .2f, base - h, x, base - h)
            cubicTo(x + half * .25f, base - h, x + half * .6f, base - h * .5f, x + half, base)
            close()
        }
        b.c.drawOval(x - half * 1.1f, base - 2f, x + half * 1.3f, base + 3f, b.pen(withAlpha(Ink.SEPIA, 80)))
        b.wash(pile, 0xFFFCFAF4.toInt(), 255)
        b.hatch(pile, 70f, 1.6f, .45f, withAlpha(0xFF6A7080.toInt(), 220), Fade(x + half, base, x, base - h, 240, 0))
        b.stipple(pile, (h * 4).toInt(), .45f, withAlpha(0xFF8A8A90.toInt(), 200), h.toInt())
        b.stroke(pile, .7f)
    }

    /** Le paludier, debout sur le talus, en profil vers la droite ; ses bras et son râteau bougent. */
    private fun worker(b: Burin) {
        // La brouette pleine de sel, derrière lui.
        val tray = poly(52f, 272f, 88f, 272f, 84f, 284f, 58f, 284f)
        b.body(tray, 0xFF8A6A48.toInt(), .45f, 0f, outline = .7f, washAlpha = 255)
        val load = Path().apply { moveTo(54f, 273f); cubicTo(60f, 262f, 80f, 262f, 86f, 273f); close() }
        b.body(load, 0xFFFCFAF4.toInt(), .25f, 70f, Fade(86f, 273f, 70f, 262f, 220, 0), outline = .6f, washAlpha = 255)
        b.c.drawCircle(56f, 288f, 5f, b.pen(Ink.SEPIA, 1.2f))
        b.line(56f, 288f, 58f, 284f, 1f); b.line(84f, 284f, 96f, 270f, 1.4f)
        b.line(66f, 284f, 64f, 292f, 1.2f)
        // Les jambes et les sabots.
        val trouser = 0xFF3A4A6A.toInt()
        b.body(limb(112f, 254f, 104f, 288f, 8f, 5f), trouser, .45f, 80f, outline = .7f, washAlpha = 255)
        b.body(limb(114f, 254f, 120f, 288f, 8f, 5f), trouser, .3f, 80f, outline = .7f, washAlpha = 255)
        for (fx in floatArrayOf(104f, 121f)) b.body(ellipse(fx + 2f, 290f, 5f, 2.4f), 0xFF5A3A22.toInt(), .4f, 0f, outline = .6f, washAlpha = 255)
        // Le torse en chemise claire, penché vers le bassin.
        val shirt = limb(112f, 252f, 118f, 232f, 14f, 15f)
        b.body(shirt, 0xFFEEE6D2.toInt(), .3f, 80f, Fade(104f, 0f, 124f, 0f, 220, 20), outline = .8f, washAlpha = 255)
        b.line(108f, 252f, 118f, 254f, 1.6f, 0xFF6A4A2A.toInt())
        // La tête et le grand chapeau noir.
        b.body(ellipse(121f, 222f, 5f, 5.6f), 0xFFE0A884.toInt(), .3f, 70f, Fade(116f, 0f, 126f, 0f, 220, 0), outline = .6f, washAlpha = 255)
        b.body(ellipse(120f, 217.5f, 10f, 2.4f), 0xFF22201E.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(115f, 210f, 125f, 218f, 2f, 2f, Path.Direction.CW) }, 0xFF22201E.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
    }

    override fun sprites(): List<Sprite> = listOf(Sprite(CLOUD, RectF(0f, 0f, 92f, 40f)) { b -> cloud(b) })

    private fun cloud(b: Burin) {
        val rnd = Random(2)
        val shape = Path()
        for (i in 0 until 5) {
            val x = 46f - 28f + i * 14f
            val r = 40f * (.22f + rnd.nextFloat() * .14f) * (1f - abs(i - 2) * .14f)
            shape.addCircle(x, 26f - r * .55f, r, Path.Direction.CW)
        }
        shape.addRect(14f, 18f, 78f, 26f, Path.Direction.CW)
        val solid = Path().apply { op(shape, Path.Op.UNION) }
        b.fill(solid, 0xFFFBF6EA.toInt())
        b.hatch(solid, 0f, 2f, .45f, Ink.BROWN, Fade(0f, 26f, 0f, 10f, 200, 0))
        b.stroke(solid, .6f, Ink.BROWN)
    }

    // ───────────── animation ─────────────

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        s.draw(c, CLOUD, dx = (t * 4f) % 380f - 60f, dy = 72f)
        c.save(); c.translate((t * 2.6f + 200f) % 380f - 60f, 98f); c.scale(.6f, .6f); s.draw(c, CLOUD, alpha = 220); c.restore()

        // Les ailes du moulin.
        ink.color = Ink.SEPIA
        val a0 = t * .9f
        for (k in 0 until 4) {
            val a = a0 + k * PI.toFloat() / 2f
            val ex = MILL_X + cos(a) * 15f; val ey = MILL_Y + sin(a) * 15f
            ink.strokeWidth = .8f; c.drawLine(MILL_X, MILL_Y, ex, ey, ink)
            val ox = -sin(a) * 2.6f; val oy = cos(a) * 2.6f
            ink.strokeWidth = 2.2f; ink.color = withAlpha(0xFFF0E8D8.toInt(), 255)
            c.drawLine(MILL_X + cos(a) * 4f + ox, MILL_Y + sin(a) * 4f + oy, ex + ox, ey + oy, ink)
            ink.color = Ink.SEPIA
        }

        // L'eau scintille.
        val frame = (t * 6f).toInt()
        ink.strokeWidth = .7f
        for (i in 0 until GLINTS) {
            val h = hash(i, frame)
            if (h < .55f) continue
            val x = GLINT[i * 2]; val y = GLINT[i * 2 + 1]
            val r = (h - .55f) * 7f
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * (h - .55f) / .45f).toInt())
            c.drawLine(x - r, y, x + r, y, ink); c.drawLine(x, y - r * .7f, x, y + r * .7f, ink)
        }

        // Le râteau : le paludier tire le sel vers lui, puis relève et repousse la planche.
        val u = (t % RAKE) / RAKE
        val pulling = u < .68f
        val f = if (pulling) 1f - smooth(u / .68f) else smooth((u - .68f) / .32f)
        val lift = if (pulling) 0f else sin((u - .68f) / .32f * PI.toFloat()) * 7f
        val hx = 156f + 78f * f; val hy = 300f - 4f * f - lift
        val h1x = 128f + 12f * f; val h1y = 247f + 2f * f
        val h2x = 138f + 14f * f; val h2y = 253f + 2f * f
        // Le tas de sel poussé par la planche grossit pendant la traction.
        if (pulling) {
            val grow = u / .68f
            fill.color = 0xFFFCFAF4.toInt()
            c.drawOval(hx - 4f - 10f * grow, hy - 1.5f - 2.5f * grow, hx - 2f, hy + 2.5f, fill)
            ink.color = withAlpha(0xFF6A7080.toInt(), 200); ink.strokeWidth = .5f
            c.drawOval(hx - 4f - 10f * grow, hy - 1.5f - 2.5f * grow, hx - 2f, hy + 2.5f, ink)
            // Les rides de l'eau derrière la planche.
            for (k in 0..1) {
                val p = ((t * 1.6f + k * .5f) % 1f)
                ink.color = withAlpha(0xFFFFFFFF.toInt(), (200 * (1f - p)).toInt()); ink.strokeWidth = .7f
                val rx = 4f + p * 14f
                c.drawArc(hx + 2f - rx * .2f, hy - rx * .25f, hx + 2f + rx * 1.6f, hy + rx * .25f, 270f, 180f, false, ink)
            }
        }
        // Le manche, d'un peu derrière les mains jusqu'à la planche.
        val dx = hx - h1x; val dy = hy - h1y
        val len = hypot(dx, dy)
        val bx = h1x - dx / len * 12f; val by = h1y - dy / len * 12f
        ink.color = Ink.SEPIA; ink.strokeWidth = 2.4f
        c.drawLine(bx, by, hx, hy, ink)
        ink.color = 0xFFB8905A.toInt(); ink.strokeWidth = 1.2f
        c.drawLine(bx, by, hx, hy, ink)
        ink.color = 0xFF6A4A2A.toInt(); ink.strokeWidth = 3.2f
        c.drawLine(hx, hy - 5f, hx + 1f, hy + 2.5f, ink)
        // Les bras : du bras arrière au bras avant.
        arm(c, 114f, 234f, h1x, h1y)
        arm(c, 119f, 233f, h2x, h2y)

        // Les mouettes.
        ink.color = Ink.SEPIA; ink.strokeWidth = .9f
        for (i in 0 until 3) {
            val p = ((t * (.05f + i * .012f) + i * .33f) % 1f)
            val x = 30f + p * 300f
            val y = 112f + i * 16f + 10f * sin(t * .7f + i * 2f)
            val w = 3f * sin(t * (3f + i) + i)
            c.drawLine(x - 6f, y - 1f - w, x - 2f, y - w * .3f, ink); c.drawLine(x - 2f, y - w * .3f, x, y + 1f, ink)
            c.drawLine(x, y + 1f, x + 2f, y - w * .3f, ink); c.drawLine(x + 2f, y - w * .3f, x + 6f, y - 1f - w, ink)
        }
    }

    /** Un bras en chemise : un trait épais cerné, et la main au bout. */
    private fun arm(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        val ex = (x0 + x1) / 2f - 2f; val ey = (y0 + y1) / 2f + 5f
        ink.color = Ink.SEPIA; ink.strokeWidth = 5.4f
        c.drawLine(x0, y0, ex, ey, ink); c.drawLine(ex, ey, x1, y1, ink)
        ink.color = 0xFFEEE6D2.toInt(); ink.strokeWidth = 3.8f
        c.drawLine(x0, y0, ex, ey, ink); c.drawLine(ex, ey, x1, y1, ink)
        fill.color = 0xFFE0A884.toInt()
        c.drawCircle(x1, y1, 2.2f, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = .5f
        c.drawCircle(x1, y1, 2.2f, ink)
    }

    private fun smooth(x: Float) = x * x * (3f - 2f * x)

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val HORIZON = 160f
        const val F = 150f
        const val DYKE = .09f
        const val MILL_X = 278f
        const val MILL_Y = 141f
        const val RAKE = 3.2f
        const val CLOUD = 1
        const val GLINTS = 26
        /** Les talus : en travers (x du monde) et en profondeur (z), du plus proche au plus loin. */
        val COLS = FloatArray(13) { -6.53f + it * 1f }
        val ROWS = floatArrayOf(.85f, 1.2f, 1.75f, 2.6f, 3.9f, 6f, 10f)
        /** Les reflets du soleil, semés sur les bassins. */
        val GLINT = FloatArray(GLINTS * 2).also { g ->
            val rnd = Random(17)
            for (i in 0 until GLINTS) {
                g[i * 2] = 60f + rnd.nextFloat() * 240f
                g[i * 2 + 1] = 176f + rnd.nextFloat() * rnd.nextFloat() * 130f
            }
        }
    }
}
