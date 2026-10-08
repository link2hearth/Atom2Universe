package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Azote — un champ de blé mûr sous le vent. L'azote fait presque tout l'air, mais les plantes
 * ne le prennent que dans le sol, d'où les engrais : pas d'azote, pas de blé. Le vent passe
 * en vagues claires sur le champ, et les grands épis du premier plan ploient à son passage.
 */
internal class SceneNitrogen : EngravedScene() {

    override val nameRes = R.string.card_scene_wheat_field
    override val noteRes = R.string.card_note_wheat_field
    override val explainRes = R.string.card_explain_wheat_field

    override fun engrave(b: Burin) {
        countryside(b)
        field(b)
    }

    private fun countryside(b: Burin) {
        // Les collines bleutées au loin, puis les haies et la ferme sur l'horizon.
        val far = Path().apply {
            moveTo(40f, 178f); cubicTo(90f, 160f, 130f, 168f, 170f, 172f)
            cubicTo(220f, 176f, 262f, 156f, 320f, 170f); lineTo(320f, HORIZON); lineTo(40f, HORIZON); close()
        }
        b.body(far, 0xFF8EA4A8.toInt(), .22f, 0f, Fade(0f, 160f, 0f, HORIZON, 40, 180), outline = .6f, washAlpha = 120)
        val near = Path().apply {
            moveTo(40f, 186f); cubicTo(100f, 178f, 150f, 184f, 200f, 182f)
            cubicTo(250f, 180f, 290f, 176f, 320f, 182f); lineTo(320f, HORIZON + 2f); lineTo(40f, HORIZON + 2f); close()
        }
        b.body(near, 0xFF7E9A5A.toInt(), .35f, 10f, outline = .6f, washAlpha = 150)
        val rnd = Random(6)
        var x = 44f
        while (x < 318f) {
            val r = 3f + rnd.nextFloat() * 3.5f
            val y = 184f - r * .6f + rnd.nextFloat() * 2f
            if (x !in 236f..274f) {
                b.body(ellipse(x, y, r, r * .85f), 0xFF4E6E3C.toInt(), .55f, 60f, Fade(x + r, 0f, x - r, 0f, 255, 60), outline = .5f, washAlpha = 200)
            }
            x += r * 1.5f + rnd.nextFloat() * 6f
        }
        // Le clocher du village, à gauche.
        b.body(rect(92f, 166f, 99f, 186f), 0xFFC9B99A.toInt(), .3f, 90f, outline = .6f)
        b.body(poly(91f, 166f, 95.5f, 152f, 100f, 166f), 0xFF7A8A96.toInt(), .45f, 60f, outline = .6f)
        // La ferme : murs clairs, toit de tuiles, une grange.
        b.body(rect(240f, 172f, 262f, 187f), 0xFFE9DCC0.toInt(), .2f, 90f, outline = .6f, washAlpha = 220)
        b.body(poly(237f, 173f, 251f, 162f, 265f, 173f), 0xFFB0523A.toInt(), .4f, 20f, outline = .6f, washAlpha = 220)
        b.body(rect(262f, 176f, 276f, 187f), 0xFFA8705A.toInt(), .45f, 90f, outline = .6f, washAlpha = 200)
        b.body(poly(260f, 177f, 269f, 170f, 278f, 177f), 0xFF8A5A44.toInt(), .5f, 20f, outline = .6f, washAlpha = 200)
        b.c.drawRect(246f, 177f, 249f, 181f, b.pen(Ink.SEPIA)); b.c.drawRect(254f, 177f, 257f, 181f, b.pen(Ink.SEPIA))
    }

    private fun field(b: Burin) {
        val field = rect(40f, HORIZON, 320f, 340f)
        b.washGradient(field, 0xFFD9AE4E.toInt(), 0f, HORIZON, 170, 0f, 320f, 255)
        // Le blé, épi par épi : minuscules au loin, de plus en plus gros vers nous.
        val rnd = Random(9)
        var y = HORIZON + 1.5f
        while (y < 336f) {
            val depth = (y - HORIZON) / (336f - HORIZON)
            val size = .7f + depth * 4.2f
            val ink = b.pen(withAlpha(Ink.SEPIA, (110 + depth * 90).toInt()), .35f + depth * .5f)
            var x = 40f + rnd.nextFloat() * size
            while (x < 322f) {
                val tilt = (rnd.nextFloat() - .3f) * size * .5f
                b.c.drawLine(x, y, x + tilt, y - size * 1.7f, ink)
                x += size * (1.1f + rnd.nextFloat() * .8f)
            }
            y += .9f + depth * 3.4f
        }
        // Les ondulations sombres du champ, là où les épis se couchent.
        for ((i, by) in floatArrayOf(206f, 236f, 276f).withIndex()) {
            val band = Path().apply {
                moveTo(40f, by); cubicTo(110f, by - 6f - i * 2f, 200f, by + 8f + i * 2f, 320f, by - 2f)
                lineTo(320f, by + 6f + i * 4f); cubicTo(200f, by + 14f + i * 5f, 110f, by + i * 3f, 40f, by + 6f + i * 3f); close()
            }
            b.hatch(band, 8f, 1.6f + i * .4f, .5f, withAlpha(0xFF6A4A1A.toInt(), 170))
        }
        // Coquelicots et bleuets.
        val flower = Random(14)
        repeat(46) {
            val fy = HORIZON + 14f + flower.nextFloat() * 120f
            val depth = (fy - HORIZON) / 140f
            val fx = 44f + flower.nextFloat() * 272f
            val r = .7f + depth * 2.2f
            val blue = flower.nextFloat() < .3f
            b.c.drawCircle(fx, fy, r, b.pen(if (blue) 0xFF4A6FC0.toInt() else 0xFFD23A28.toInt()))
            b.c.drawCircle(fx, fy, r * .35f, b.pen(Ink.SEPIA))
        }
    }

    override fun sprites(): List<Sprite> {
        val list = arrayListOf(
            Sprite(EAR, RectF(-26f, -104f, 26f, 6f)) { b -> ear(b) },
            Sprite(SHEEN, RectF(-60f, -12f, 60f, 12f)) { b ->
                b.c.save(); b.c.scale(1f, .2f)
                b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 60f,
                    intArrayOf(0x88FFF4C8.toInt(), 0x33FFF4C8, 0x00FFF4C8), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                b.c.drawCircle(0f, 0f, 60f, b.p)
                b.c.restore()
            },
            Sprite(CLOUD, RectF(0f, 0f, 92f, 40f)) { b -> cloud(b, 46f, 26f, 40f) }
        )
        return list
    }

    /** Un épi de blé mûr, gravé debout, sa base en (0, 0) : épillets alternés et longues barbes. */
    private fun ear(b: Burin) {
        val spikelets = 13
        // Les barbes d'abord : elles passent derrière les grains.
        for (i in 0 until spikelets) {
            val side = if (i % 2 == 0) -1f else 1f
            val y = -8f - i * 4.9f
            val size = 1f - i * .03f
            val tipX = side * 6.5f * size; val tipY = y - 9f * size
            val reach = 20f + (spikelets - i) * .6f
            b.taper(tipX, tipY, tipX + side * 1.5f, tipY - reach * .35f, tipX + side * 3f, tipY - reach * .7f, tipX + side * 4.5f, tipY - reach, .7f, Ink.SEPIA)
        }
        b.taper(0f, -70f, .3f, -78f, .5f, -86f, .6f, -94f, .7f, Ink.SEPIA)
        // La tige qui traverse l'épi.
        b.line(0f, 4f, 0f, -70f, 1.4f, Ink.BROWN)
        for (i in 0 until spikelets) {
            val side = if (i % 2 == 0) -1f else 1f
            val y = -8f - i * 4.9f
            val size = 1f - i * .03f
            val grain = Path().apply {
                moveTo(side * .5f, y + 3f * size)
                cubicTo(side * 8.5f * size, y + 2f * size, side * 10f * size, y - 6f * size, side * 6.5f * size, y - 9f * size)
                cubicTo(side * 2.5f * size, y - 8f * size, -side * .5f, y - 3f * size, side * .5f, y + 3f * size); close()
            }
            b.wash(grain, 0xFFE8BC56.toInt(), 255)
            b.hatch(grain, if (side < 0) 60f else 120f, 1.25f, .45f, Ink.SEPIA, Fade(side * 9f, y, side * 2f, y - 6f, 240, 20))
            b.stroke(grain, .75f)
            b.line(side * 2.5f * size, y - 1f, side * 6.5f * size, y - 7f * size, .45f, Ink.BROWN)
        }
        // Le dernier épillet, au sommet.
        val top = Path().apply {
            moveTo(-3f, -68f); cubicTo(-4f, -74f, -1f, -79f, 0f, -80f); cubicTo(1f, -79f, 4f, -74f, 3f, -68f); close()
        }
        b.body(top, 0xFFE8BC56.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
    }

    private fun cloud(b: Burin, cx: Float, base: Float, w: Float) {
        val rnd = Random(3)
        val shape = Path()
        for (i in 0 until 5) {
            val x = cx - w * .7f + i * w * 1.4f / 4
            val r = w * (.22f + rnd.nextFloat() * .14f) * (1f - abs(i - 2) * .14f)
            shape.addCircle(x, base - r * .55f, r, Path.Direction.CW)
        }
        shape.addRect(cx - w * .8f, base - w * .2f, cx + w * .8f, base, Path.Direction.CW)
        val solid = Path().apply { op(shape, Path.Op.UNION) }
        b.fill(solid, 0xFFFBF6EA.toInt())
        b.hatch(solid, 0f, 2f, .45f, Ink.BROWN, Fade(0f, base, 0f, base - w * .4f, 200, 0))
        b.stroke(solid, .6f, Ink.BROWN)
    }

    // ───────────── animation ─────────────

    private val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pts = FloatArray(64)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        s.draw(c, CLOUD, dx = drift(t, 5f, 0f), dy = 76f)
        c.save(); c.translate(drift(t, 3.5f, 190f), 104f); c.scale(.7f, .7f); s.draw(c, CLOUD, alpha = 230); c.restore()

        // Les vagues de vent sur le champ : plus grandes et plus rapides vers nous.
        c.save(); c.clipRect(40f, HORIZON, 320f, 340f)
        for ((i, row) in WAVES.withIndex()) {
            val x = wave(t, row[1], row[2])
            c.save(); c.translate(x, row[0]); c.scale(row[3], row[3])
            s.draw(c, SHEEN, alpha = 210)
            c.restore()
            if (i == 2) { c.save(); c.translate(x - 210f, row[0] + 8f); c.scale(row[3], row[3]); s.draw(c, SHEEN, alpha = 160); c.restore() }
        }
        c.restore()

        // Deux hirondelles qui rasent le champ.
        stem.color = Ink.SEPIA; stem.strokeWidth = 1f
        for (i in 0..1) {
            val p = ((t * .11f + i * .5f) % 1f)
            val x = 30f + p * 300f
            val y = 140f + i * 26f + 16f * sin(p * 9f + i)
            val wing = 3.8f * sin(t * 11f + i * 2f)
            c.drawLine(x - 6f, y - wing, x, y, stem); c.drawLine(x, y, x + 6f, y - wing, stem)
            c.drawLine(x, y, x - 4f, y + 2.5f, stem)
        }

        // Les grands épis : chacun se balance, et ploie quand la vague de devant le traverse.
        val front = wave(t, WAVES[2][1], WAVES[2][2])
        for (e in EARS) {
            val gx = e[0]; val len = e[1]; val scale = e[2]; val phase = e[3]
            val gust = exp(-((gx - front) / 46f).let { it * it })
            val deg = 2.6f * sin(t * 1.15f + phase) + 1.2f * sin(t * 2.7f + phase * 2f) + 9f * gust
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val tipX = gx + sin(rad) * len; val tipY = GROUND - cos(rad) * len
            val midX = gx + sin(rad) * len * .35f; val midY = GROUND - len * .5f
            var n = 0
            var lx = gx; var ly = GROUND
            for (k in 1..8) {
                val u = k / 8f; val v = 1 - u
                val x = v * v * gx + 2 * v * u * midX + u * u * tipX
                val y = v * v * GROUND + 2 * v * u * midY + u * u * tipY
                pts[n++] = lx; pts[n++] = ly; pts[n++] = x; pts[n++] = y
                lx = x; ly = y
            }
            stem.color = Ink.SEPIA; stem.strokeWidth = 2.3f * scale
            c.drawLines(pts, 0, n, stem)
            stem.color = 0xFFE6C877.toInt(); stem.strokeWidth = 1.2f * scale
            c.drawLines(pts, 0, n, stem)
            c.save(); c.translate(tipX, tipY); c.scale(scale, scale)
            s.draw(c, EAR, deg = deg * 1.4f)
            c.restore()
        }
    }

    private fun wave(t: Float, speed: Float, offset: Float) = (t * speed + offset) % 520f - 110f

    private fun drift(t: Float, speed: Float, offset: Float) = (t * speed + offset) % 380f - 52f

    private companion object {
        const val HORIZON = 188f
        const val GROUND = 336f
        const val EAR = 1
        const val SHEEN = 2
        const val CLOUD = 3
        /** Les vagues : hauteur, vitesse, décalage, taille. */
        val WAVES = arrayOf(
            floatArrayOf(204f, 22f, 0f, .7f),
            floatArrayOf(236f, 32f, 260f, 1.1f),
            floatArrayOf(282f, 44f, 120f, 1.7f)
        )
        /** Les épis du premier plan : pied, longueur de tige, taille, phase ; du fond vers l'avant. */
        val EARS = arrayOf(
            floatArrayOf(252f, 104f, .95f, 2.1f),
            floatArrayOf(98f, 112f, 1f, .4f),
            floatArrayOf(290f, 96f, 1.05f, 3.2f),
            floatArrayOf(176f, 136f, 1.1f, 1.3f),
            floatArrayOf(60f, 104f, 1.15f, 4.4f),
            floatArrayOf(214f, 132f, 1.2f, 5.1f),
            floatArrayOf(132f, 144f, 1.25f, 2.7f)
        )
    }
}
