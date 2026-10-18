package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phosphore — l'allumette. Le grattoir de la boîte contient du phosphore rouge : frotter la
 * tête dessus en change une pincée en phosphore blanc, qui s'enflamme aussitôt. La main frotte,
 * l'allumette s'embrase, brûle en noircissant, puis on la secoue et elle s'éteint en fumant.
 */
internal class ScenePhosphorus : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_match
    override val noteRes = R.string.card_note_match
    override val explainRes = R.string.card_explain_match

    override fun engrave(b: Burin) {
        room(b)
        box(b)
    }

    private fun room(b: Burin) {
        val wall = rect(40f, 56f, 320f, 244f)
        b.fill(wall, 0xFF2A2220.toInt())
        b.hatch(wall, 0f, 1.8f, .5f, 0xFF120E0C.toInt())
        b.hatch(wall, 70f, 2.6f, .4f, 0xFF120E0C.toInt(), Fade(0f, 56f, 0f, 240f, 200, 60))
        val table = rect(40f, 240f, 320f, 340f)
        b.body(table, 0xFF5A3A24.toInt(), .5f, 0f, Fade(0f, 240f, 0f, 330f, 120, 255), outline = .8f, washAlpha = 255)
        for (i in 0 until 7) {
            val gy = 246f + i * 11f
            b.stroke(Path().apply { moveTo(40f, gy); cubicTo(120f, gy - 3f, 220f, gy + 4f, 320f, gy - 1f) }, .5f, withAlpha(0xFF1A0E08.toInt(), 200))
        }
        // Deux allumettes déjà brûlées, sur la table.
        for ((x, y, a) in listOf(Triple(250f, 300f, -6f), Triple(262f, 310f, 8f))) {
            b.c.save(); b.c.rotate(a, x, y)
            b.c.drawRect(x, y - 1.6f, x + 62f, y + 1.6f, b.pen(0xFFD8B880.toInt()))
            b.c.drawRect(x, y - 1.6f, x + 20f, y + 1.6f, b.pen(0xFF2A1A12.toInt()))
            b.c.drawRect(x, y - 1.6f, x + 62f, y + 1.6f, b.pen(Ink.SEPIA, .5f))
            b.c.drawOval(x - 4f, y - 2.6f, x + 3f, y + 2.6f, b.pen(0xFF1A1210.toInt()))
            b.c.restore()
        }
    }

    /** La boîte d'allumettes, vue de trois quarts, son grattoir tourné vers nous. */
    private fun box(b: Burin) {
        b.c.drawOval(72f, 270f, 240f, 300f, b.pen(withAlpha(0xFF000000.toInt(), 90)))
        val top = poly(78f, 252f, 206f, 246f, 226f, 262f, 98f, 268f)
        b.body(top, 0xFFE0C890.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        // L'étiquette : un cadre rouge et une étoile, sans texte.
        val label = poly(100f, 253f, 190f, 249f, 204f, 259f, 114f, 263f)
        b.body(label, 0xFFB8302A.toInt(), .25f, 0f, outline = .6f, washAlpha = 255)
        val star = Path()
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) 6f else 2.6f
            val a = -Math.PI / 2 + k * Math.PI / 5
            val x = 152f + cos(a).toFloat() * r; val y = 256f + sin(a).toFloat() * r * .45f
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        b.gold(star)
        val front = poly(98f, 268f, 226f, 262f, 226f, 282f, 98f, 288f)
        b.body(front, 0xFFC8A870.toInt(), .45f, 0f, Fade(0f, 268f, 0f, 288f, 80, 230), outline = .9f, washAlpha = 255)
        val end = poly(78f, 252f, 98f, 268f, 98f, 288f, 78f, 272f)
        b.body(end, 0xFFA88850.toInt(), .6f, 80f, outline = .9f, washAlpha = 255)
        // Le grattoir : poudre de verre et phosphore rouge.
        b.body(strip, 0xFF7A2A1E.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        b.stipple(strip, 260, .55f, withAlpha(0xFF2A0A06.toInt(), 230), 3)
        b.stipple(strip, 80, .45f, withAlpha(0xFFE8A890.toInt(), 200), 4)
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(MATCH, RectF(-8f, -7f, 80f, 7f)) { b ->
            val stick = rect(3f, -1.8f, 76f, 1.8f)
            b.body(stick, 0xFFE8CC94.toInt(), .2f, 0f, Fade(0f, 1.8f, 0f, -1f, 230, 0), outline = .55f, washAlpha = 255)
            val head = ellipse(0f, 0f, 6f, 4.4f)
            b.body(head, 0xFFA8301E.toInt(), .35f, 30f, Fade(4f, 4f, -2f, -2f, 230, 0), outline = .7f, washAlpha = 255)
            b.c.drawCircle(-2f, -1.6f, 1.2f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 160)))
        },
        Sprite(HAND, RectF(50f, -34f, 150f, 44f)) { b -> hand(b) },
        Sprite(GLOW, RectF(-80f, -80f, 80f, 80f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 80f,
                intArrayOf(0xCCFFE6A0.toInt(), 0x66FFB050, 0x22FF8A30, 0x00FF8A30), floatArrayOf(0f, .2f, .55f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 80f, b.p)
        }
    )

    /**
     * La main qui tient l'allumette, dans le repère de l'allumette : le bâton va de 0 (la tête)
     * à 76 vers la droite ; le pouce dessus, l'index dessous, le poing et la manchette au-delà.
     */
    private fun hand(b: Burin) {
        val skin = 0xFFE0A884.toInt()
        val sleeve = Path().apply { addRoundRect(118f, -16f, 150f, 40f, 6f, 6f, Path.Direction.CW) }
        b.body(sleeve, 0xFF2E3A5A.toInt(), .55f, 80f, outline = .8f, washAlpha = 255)
        b.body(rect(112f, -12f, 122f, 36f), 0xFFF4EEE2.toInt(), .2f, 90f, outline = .7f, washAlpha = 255)
        b.c.drawCircle(117f, 26f, 1.4f, b.pen(Ink.SEPIA, .5f))
        val fist = Path().apply {
            moveTo(70f, 4f); cubicTo(74f, 14f, 86f, 30f, 104f, 32f)
            cubicTo(114f, 33f, 116f, 20f, 116f, 8f); cubicTo(116f, -6f, 110f, -18f, 98f, -20f)
            cubicTo(88f, -21f, 80f, -12f, 74f, -6f); close()
        }
        b.body(fist, skin, .35f, 60f, Fade(110f, 30f, 84f, -6f, 230, 0), outline = .9f, washAlpha = 255)
        // Les doigts repliés sous le poing.
        for (k in 0 until 3) b.stroke(Path().apply { moveTo(84f + k * 8f, 26f + k * 1.5f); cubicTo(88f + k * 8f, 30f, 94f + k * 8f, 31f, 98f + k * 7f, 28f) }, .6f)
        // L'index, sous le bâton, et le pouce, dessus.
        val index = Path().apply {
            moveTo(60f, 2f); cubicTo(60f, 7f, 66f, 10f, 76f, 10f); cubicTo(84f, 10f, 88f, 6f, 86f, 2f)
            cubicTo(80f, 2.5f, 68f, 2.5f, 60f, 2f); close()
        }
        b.body(index, skin, .3f, 70f, Fade(70f, 10f, 70f, 2f, 230, 0), outline = .8f, washAlpha = 255)
        b.c.drawArc(57f, 0f, 63f, 5f, 90f, 180f, false, b.pen(withAlpha(0xFFF4E0D0.toInt(), 255), 1f))
        val thumb = Path().apply {
            moveTo(58f, -2f); cubicTo(58f, -8f, 66f, -12f, 78f, -12f); cubicTo(92f, -12f, 100f, -8f, 100f, -2f)
            cubicTo(88f, -3f, 70f, -2.4f, 58f, -2f); close()
        }
        b.body(thumb, skin, .3f, 60f, Fade(80f, -12f, 80f, -2f, 0, 220), outline = .8f, washAlpha = 255)
        b.c.drawOval(58f, -7f, 66f, -2.5f, b.pen(withAlpha(0xFFF8E8DC.toInt(), 230)))
        b.c.drawOval(58f, -7f, 66f, -2.5f, b.pen(Ink.SEPIA, .4f))
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val flame = Path()
    private val pts = FloatArray(128)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val u = t % CYCLE
        var hx: Float; var hy: Float; var a: Float
        var size = 0f
        var flare = 0f
        var char = 0f
        var shift = 0f
        var smoke = -1f
        when {
            u < .8f -> {
                val p = u / .8f
                val e = p * p * (3f - 2f * p)
                hx = S0X + (S1X - S0X) * e; hy = S0Y + (S1Y - S0Y) * e; a = -8f + 2f * sin(p * 30f)
                if (p > .7f) sparks(c, hx, hy, t, (p - .7f) / .3f)
            }
            u < 2f -> {
                val p = ((u - .8f) / 1.2f).coerceIn(0f, 1f)
                val lift = ((u - 1.2f) / .8f).coerceIn(0f, 1f)
                val e = lift * lift * (3f - 2f * lift)
                hx = S1X + (LX - S1X) * e; hy = S1Y + (LY - S1Y) * e; a = -8f - 7f * e
                flare = (1f - (u - .8f) / .45f).coerceIn(0f, 1f)
                size = .6f + .4f * p
                char = p * 6f
                if (u < 1.25f) sparks(c, hx, hy, t, 1f - (u - .8f) / .45f)
            }
            u < 5.4f -> {
                hx = LX + .8f * sin(t * 1.7f); hy = LY + .6f * sin(t * 2.3f); a = -15f
                size = 1f
                char = 6f + (u - 2f) / 3.4f * 26f
            }
            u < 5.9f -> {
                val p = (u - 5.4f) / .5f
                hx = LX; hy = LY; a = -15f + 14f * sin(p * 28f) * (1f - p)
                size = (1f - p * 1.6f).coerceAtLeast(0f)
                char = 32f
                smoke = p * .5f
            }
            u < 6.6f -> {
                val p = (u - 5.9f) / .7f
                hx = LX; hy = LY; a = -15f
                char = 32f
                shift = p * p * 170f
                smoke = .25f + p
            }
            else -> {
                val p = (u - 6.6f) / (CYCLE - 6.6f)
                val e = 1f - (1f - p) * (1f - p)
                hx = S0X; hy = S0Y; a = -8f
                shift = (1f - e) * 170f
            }
        }
        // La lumière de la flamme éclaire la pièce.
        val light = (size * (.92f + .08f * sin(t * 23f) * sin(t * 7f)) + flare * .8f).coerceAtMost(1.6f)
        if (light > .02f) {
            c.save(); c.translate(hx, hy - 10f); val g = 1.3f + .5f * flare; c.scale(g, g)
            s.draw(c, GLOW, alpha = (230 * light.coerceAtMost(1f)).toInt())
            c.restore()
        }
        // La fumée qui monte après l'extinction.
        if (smoke >= 0f) smokeRibbon(c, LX - 2f, LY - 4f, smoke, t)
        // L'allumette, son bois qui noircit, et la main.
        c.save(); c.translate(hx + shift, hy); c.rotate(a)
        s.draw(c, MATCH)
        if (char > 0f) {
            fill.color = 0xFF2A1A12.toInt()
            c.drawRect(3f, -1.9f, 3f + char, 1.9f, fill)
            c.drawOval(-6.2f, -4.6f, 6.2f, 4.6f, fill)
            fill.color = withAlpha(0xFFFF7A2A.toInt(), (180 * size).toInt())
            c.drawRect(3f + char - 2f, -1.9f, 3f + char, 1.9f, fill)
        }
        s.draw(c, HAND)
        c.restore()
        // La flamme, toujours dressée vers le haut.
        if (size > .02f) drawFlame(c, hx + shift, hy, size, t)
        if (flare > 0f) {
            fill.color = withAlpha(0xFFFFFBE8.toInt(), (240 * flare).toInt())
            c.drawCircle(hx, hy - 4f, 6f + 10f * flare, fill)
        }
    }

    private fun drawFlame(c: Canvas, x: Float, y: Float, size: Float, t: Float) {
        val h = (30f + 3f * sin(t * 11f) + 2f * sin(t * 17f)) * size
        val w = 7.5f * size
        val sway = 2.2f * sin(t * 6.3f) + 1.2f * sin(t * 13.7f)
        for (layer in 0 until 3) {
            val k = 1f - layer * .3f
            flame.reset()
            flame.moveTo(x + sway * k, y - 2f - h * k)
            flame.cubicTo(x + w * 1.15f * k, y - h * .45f * k, x + w * k, y + 2f, x, y + 3f)
            flame.cubicTo(x - w * k, y + 2f, x - w * 1.15f * k, y - h * .45f * k, x + sway * k, y - 2f - h * k)
            flame.close()
            fill.color = when (layer) {
                0 -> withAlpha(0xFFFF8A2A.toInt(), 210)
                1 -> withAlpha(0xFFFFD25A.toInt(), 235)
                else -> withAlpha(0xFFFFF8E0.toInt(), 255)
            }
            c.drawPath(flame, fill)
        }
        fill.color = withAlpha(0xFF4A7AE8.toInt(), (150 * size).toInt())
        c.drawOval(x - 3.6f * size, y - 3f * size, x + 3.6f * size, y + 2.6f * size, fill)
    }

    /** Les étincelles du frottement et de l'embrasement. */
    private fun sparks(c: Canvas, x: Float, y: Float, t: Float, strength: Float) {
        if (strength <= 0f) return
        val frame = (t * 20f).toInt()
        ink.strokeWidth = 1.1f
        for (k in 0 until 9) {
            val ang = -Math.PI.toFloat() * (.1f + hash(k, frame, 1) * .8f)
            val r = 6f + hash(k, frame, 2) * 16f * strength
            ink.color = withAlpha(if (k % 2 == 0) 0xFFFFE6A0.toInt() else 0xFFFF9A40.toInt(), (255 * strength).toInt())
            val sx = x + cos(ang) * r; val sy = y + sin(ang) * r
            c.drawLine(sx, sy, sx + cos(ang) * 3f, sy + sin(ang) * 3f, ink)
        }
    }

    /** Un filet de fumée qui monte en ondulant et s'efface. */
    private fun smokeRibbon(c: Canvas, x: Float, y: Float, age: Float, t: Float) {
        val alpha = (1f - age / 1.2f).coerceIn(0f, 1f)
        if (alpha <= 0f) return
        var n = 0
        var lx = x; var ly = y
        val rise = 30f + age * 50f
        for (k in 1..14) {
            val v = k / 14f
            val nx = x + v * v * 10f * sin(t * 1.5f + v * 6f) + v * 6f
            val ny = y - v * rise
            pts[n++] = lx; pts[n++] = ly; pts[n++] = nx; pts[n++] = ny
            lx = nx; ly = ny
        }
        ink.color = withAlpha(0xFFB8B0A8.toInt(), (150 * alpha).toInt()); ink.strokeWidth = 1.6f
        c.drawLines(pts, 0, n, ink)
    }

    private fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private val strip = poly(102f, 272f, 222f, 266f, 222f, 277f, 102f, 283f)

    private companion object {
        const val CYCLE = 7.4f
        const val S0X = 212f
        const val S0Y = 271f
        const val S1X = 112f
        const val S1Y = 277f
        const val LX = 136f
        const val LY = 196f
        const val MATCH = 1
        const val HAND = 2
        const val GLOW = 3
    }
}
