package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hélium — un lâcher de ballons au-dessus de la ville. Le gaz est bien plus léger que l'air :
 * la grappe tire sur ses ficelles, les ballons se bousculent, et deux échappés montent au loin.
 */
internal class SceneHelium : EngravedScene() {

    override val nameRes = R.string.card_scene_balloon_release
    override val noteRes = R.string.card_note_balloon_release
    override val explainRes = R.string.card_explain_balloon_release

    override fun engrave(b: Burin) {
        landscape(b)
    }

    private fun landscape(b: Burin) {
        // Collines lointaines, à peine gravées.
        val hills = Path().apply {
            moveTo(40f, 268f)
            cubicTo(90f, 248f, 120f, 256f, 160f, 258f)
            cubicTo(200f, 260f, 240f, 246f, 320f, 262f)
            lineTo(320f, 280f); lineTo(40f, 280f); close()
        }
        b.body(hills, 0xFF8FA58A.toInt(), .22f, 0f, Fade(0f, 250f, 0f, 280f, 60, 200), outline = .6f, washAlpha = 110)
        // La ville : toits, clochers, un dôme.
        val rnd = Random(4)
        var x = 96f
        while (x < 300f) {
            val w = 8f + rnd.nextFloat() * 10f
            val h = 6f + rnd.nextFloat() * 9f
            val house = poly(x, 280f, x, 280f - h, x + w / 2f, 276f - h - 4f, x + w, 280f - h, x + w, 280f)
            b.body(house, 0xFFB9A88C.toInt(), .3f, 90f, outline = .5f, washAlpha = 120)
            x += w + 1f
        }
        val dome = Path().apply { moveTo(232f, 270f); cubicTo(232f, 252f, 252f, 252f, 252f, 270f); close() }
        b.body(rect(230f, 270f, 254f, 280f), 0xFFB9A88C.toInt(), .3f, 90f, outline = .5f)
        b.body(dome, 0xFF8FA0A8.toInt(), .35f, 60f, Fade(252f, 270f, 232f, 254f, 230, 30), outline = .6f)
        b.line(242f, 255f, 242f, 246f, .7f)
        // Le parc : des arbres, la pelouse, et la foule qui lève la tête.
        for (i in 0 until 9) {
            val tx = 52f + i * 31f + (if (i % 2 == 0) 0f else 4f)
            tree(b, tx, 292f, 12f + (i % 3) * 3f)
        }
        val lawn = rect(40f, 288f, 320f, 340f)
        b.body(lawn, 0xFF7E9A5A.toInt(), 0f, outline = 0f, washAlpha = 120)
        b.hatch(lawn, 0f, 2.4f, .45f, Ink.SEPIA, Fade(0f, 290f, 0f, 330f, 90, 220))
        b.line(40f, 288f, 320f, 288f, .8f)
        val coats = intArrayOf(0xFF6A3A2A.toInt(), 0xFF2F4A6A.toInt(), 0xFF8A2A22.toInt(), 0xFF4A5A3A.toInt(), 0xFF7A6A52.toInt())
        for (row in 0 until 3) {
            val py = 298f + row * 5f
            val s = .8f + row * .15f
            var px = 44f + rnd.nextFloat() * 3f
            while (px < 318f) {
                val jitter = (rnd.nextFloat() - .5f) * 1.4f
                b.c.drawOval(px - 1.6f * s, py - 1.2f * s + jitter, px + 1.6f * s, py + 3.4f * s + jitter, b.pen(withAlpha(coats[rnd.nextInt(coats.size)], 210)))
                b.c.drawCircle(px, py - 2.2f * s + jitter, 1.15f * s, b.pen(if (rnd.nextFloat() < .3f) Ink.SEPIA else 0xFF8C6A50.toInt()))
                px += (4.2f + rnd.nextFloat() * 2.4f) * s
            }
        }
    }

    private fun tree(b: Burin, x: Float, base: Float, r: Float) {
        b.line(x, base, x, base - r, 1.4f)
        val crown = ellipse(x, base - r * 1.6f, r * .75f, r)
        b.body(crown, 0xFF5E8A48.toInt(), .45f, 60f, Fade(x + r, 0f, x - r, 0f, 255, 40), outline = .6f, washAlpha = 150)
    }

    override fun sprites(): List<Sprite> {
        val list = ArrayList<Sprite>()
        for ((i, color) in COLORS.withIndex()) list += Sprite(i, RectF(-20f, -24f, 20f, 28f)) { b -> balloon(b, color) }
        list += Sprite(CLOUD_A, RectF(0f, 0f, 92f, 40f)) { b -> cloud(b, 46f, 26f, 40f, 1) }
        list += Sprite(CLOUD_B, RectF(0f, 0f, 70f, 32f)) { b -> cloud(b, 35f, 21f, 30f, 2) }
        return list
    }

    /** Un ballon de baudruche, gravé autour de (0, 0) : le nœud en bas, le reflet en haut à gauche. */
    private fun balloon(b: Burin, color: Int) {
        val shape = Path().apply {
            moveTo(0f, -21f)
            cubicTo(11f, -21f, 17f, -12f, 17f, -3f)
            cubicTo(17f, 9f, 7f, 18f, 0f, 22f)
            cubicTo(-7f, 18f, -17f, 9f, -17f, -3f)
            cubicTo(-17f, -12f, -11f, -21f, 0f, -21f); close()
        }
        b.wash(shape, color, 235)
        b.hatch(shape, 35f, 1.9f, .5f, Ink.SEPIA, Fade(12f, 14f, -3f, -5f, 230, 0))
        b.hatch(shape, 110f, 2.4f, .45f, Ink.SEPIA, Fade(16f, 16f, 6f, 5f, 200, 0))
        // Le reflet : une tache claire, puis un point.
        b.c.save(); b.c.rotate(-28f, -7f, -10f)
        b.c.drawOval(-10.5f, -17f, -3.5f, -3f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 190)))
        b.c.restore()
        b.c.drawCircle(-3f, -16.5f, 1.2f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 220)))
        b.stroke(shape, 1f)
        // Le nœud.
        val knot = poly(-2.4f, 25.5f, 2.4f, 25.5f, 0f, 21.5f)
        b.body(knot, color, .4f, 90f, outline = .6f, washAlpha = 255)
    }

    private fun cloud(b: Burin, cx: Float, base: Float, w: Float, seed: Int) {
        val rnd = Random(seed)
        val shape = Path()
        val lobes = 5
        for (i in 0 until lobes) {
            val x = cx - w * .7f + i * w * 1.4f / (lobes - 1)
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

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val ribbon = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val pts = FloatArray(512)
    private val tipX = FloatArray(BUNCH.size); private val tipY = FloatArray(BUNCH.size)
    private val posX = FloatArray(BUNCH.size); private val posY = FloatArray(BUNCH.size)
    private val angle = FloatArray(BUNCH.size)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        s.draw(c, CLOUD_A, dx = drift(t, 7f, 0f), dy = 74f)
        s.draw(c, CLOUD_B, dx = drift(t, 4.5f, 170f), dy = 176f, alpha = 230)

        // Deux échappés qui montent au loin, puis reviennent d'en bas.
        for (j in 0..1) {
            val p = ((t + j * 13f) % 26f) / 26f
            val y = 262f - p * 300f
            val x = 76f + j * 196f + 12f * sin(t * .6f + j * 2f) + p * 18f * (if (j == 0) 1f else -1f)
            val a = when { p < .06f -> p / .06f; p > .92f -> (1f - p) / .08f; else -> 1f }
            c.save(); c.translate(x, y); c.scale(.42f, .42f)
            c.rotate(6f * sin(t * 1.3f + j))
            ink.color = withAlpha(Ink.SEPIA, (200 * a).toInt()); ink.strokeWidth = 1.2f
            c.drawLine(0f, 25f, 3f * sin(t * 3f + j), 60f, ink)
            s.draw(c, (j * 3 + 2) % COLORS.size, alpha = (255 * a).toInt())
            c.restore()
        }

        // La grappe : elle dérive un peu, et chaque ballon tire sur sa ficelle à sa façon.
        val bx = 5f * sin(t * .35f) + 2f * sin(t * .9f)
        val by = -4f * sin(t * .5f)
        val kx = KNOT_X + bx * .6f + 1.5f * sin(t * .7f); val ky = KNOT_Y + by * .7f
        for ((i, spec) in BUNCH.withIndex()) {
            val x = spec[0] + bx + 3f * sin(t * .9f + i * 1.3f) + 1.5f * sin(t * 2.1f + i)
            val y = spec[1] + by + 2.5f * sin(t * 1.1f + i * .7f)
            val deg = Math.toDegrees(atan2((x - kx).toDouble(), (ky - y).toDouble())).toFloat()
            val r = Math.toRadians(deg.toDouble()).toFloat()
            val l = 25.5f * spec[3]
            posX[i] = x; posY[i] = y; angle[i] = deg
            tipX[i] = x - sin(r) * l; tipY[i] = y + cos(r) * l
        }
        // Les ficelles d'abord : elles passent derrière les ballons.
        var n = 0
        for (i in BUNCH.indices) {
            val mx = (tipX[i] + kx) / 2f + 2f * sin(t * 1.3f + i); val my = (tipY[i] + ky) / 2f
            var lx = tipX[i]; var ly = tipY[i]
            for (k in 1..8) {
                val u = k / 8f; val v = 1 - u
                val x = v * v * tipX[i] + 2 * v * u * mx + u * u * kx
                val y = v * v * tipY[i] + 2 * v * u * my + u * u * ky
                pts[n++] = lx; pts[n++] = ly; pts[n++] = x; pts[n++] = y
                lx = x; ly = y
            }
        }
        ink.color = withAlpha(Ink.SEPIA, 200); ink.strokeWidth = .55f
        c.drawLines(pts, 0, n, ink)
        // Le ruban noué, qui pend et frise.
        n = 0
        var lx = kx; var ly = ky
        for (k in 1..16) {
            val u = k / 16f
            val x = kx + 4.5f * u * sin(u * 11f + t * 2.2f) + 3f * u * sin(t * .8f)
            val y = ky + u * 40f
            pts[n++] = lx; pts[n++] = ly; pts[n++] = x; pts[n++] = y
            lx = x; ly = y
        }
        ribbon.color = 0xFFC8452F.toInt(); ribbon.strokeWidth = 1.4f
        c.drawLines(pts, 0, n, ribbon)
        ribbon.strokeWidth = 1.1f
        c.drawOval(kx - 6f, ky - 2.5f, kx, ky + 1.5f, ribbon)
        c.drawOval(kx, ky - 2.5f, kx + 6f, ky + 1.5f, ribbon)
        // Puis les ballons, du fond vers l'avant.
        for ((i, spec) in BUNCH.withIndex()) {
            c.save(); c.translate(posX[i], posY[i]); c.scale(spec[3], spec[3])
            s.draw(c, spec[2].toInt(), deg = angle[i])
            c.restore()
        }

        // Deux oiseaux au loin.
        ink.color = Ink.SEPIA; ink.strokeWidth = .8f
        for (i in 0..1) {
            val x = 60f + ((t * 9f + i * 140f) % 260f)
            val y = 216f + i * 22f + 4f * sin(t * .9f + i)
            val wing = 3.5f * sin(t * 9f + i * 2f)
            c.drawLine(x - 5f, y - wing, x, y, ink)
            c.drawLine(x, y, x + 5f, y - wing, ink)
        }
    }

    /** Un nuage qui traverse la fenêtre de gauche à droite, puis revient de l'autre côté. */
    private fun drift(t: Float, speed: Float, offset: Float): Float {
        val span = 380f
        return (t * speed + offset) % span - 92f + 40f
    }

    private companion object {
        val COLORS = intArrayOf(
            0xFFD0402E.toInt(), 0xFFF0C445.toInt(), 0xFF3F6FB8.toInt(), 0xFF4C9A55.toInt(),
            0xFFE57F2E.toInt(), 0xFFE07AA0.toInt(), 0xFF7E5BAE.toInt(), 0xFF5FB3C8.toInt()
        )
        /** La grappe : x, y, couleur, taille ; du fond vers l'avant. */
        val BUNCH = arrayOf(
            floatArrayOf(150f, 104f, 2f, .95f), floatArrayOf(186f, 94f, 0f, 1f), floatArrayOf(220f, 108f, 1f, .95f),
            floatArrayOf(128f, 138f, 3f, .95f), floatArrayOf(164f, 132f, 5f, 1f), floatArrayOf(200f, 132f, 4f, 1f),
            floatArrayOf(234f, 142f, 6f, .95f),
            floatArrayOf(152f, 168f, 1f, 1.02f), floatArrayOf(190f, 166f, 7f, 1.02f), floatArrayOf(222f, 172f, 0f, .98f)
        )
        const val KNOT_X = 182f
        const val KNOT_Y = 240f
        const val CLOUD_A = 100
        const val CLOUD_B = 101
    }
}
