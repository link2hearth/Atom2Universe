package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soufre — la solfatare : un volcan dont le cratère et les fumerolles déposent des croûtes
 * jaunes de soufre natif. Le panache monte et dérive ; au premier plan, des cristaux de soufre.
 */
internal class SceneSulfur : EngravedScene() {

    override val nameRes = R.string.card_scene_solfatara
    override val noteRes = R.string.card_note_solfatara
    override val explainRes = R.string.card_explain_solfatara

    override val sky = Sky.DUSK

    private val cone = Path().apply {
        moveTo(40f, 292f)
        cubicTo(90f, 270f, 128f, 212f, 150f, 152f)
        lineTo(166f, 160f); lineTo(198f, 160f); lineTo(214f, 149f)
        cubicTo(236f, 206f, 272f, 262f, 320f, 284f)
        lineTo(320f, 330f); lineTo(40f, 330f); close()
    }

    override fun engrave(b: Burin) {
        distant(b)
        volcano(b)
        crystals(b)
    }

    private fun distant(b: Burin) {
        val ridge = Path().apply {
            moveTo(40f, 246f); cubicTo(70f, 232f, 92f, 236f, 112f, 244f)
            cubicTo(240f, 226f, 280f, 240f, 320f, 232f); lineTo(320f, 300f); lineTo(40f, 300f); close()
        }
        b.body(ridge, 0xFF9A8FA6.toInt(), .3f, 0f, Fade(0f, 230f, 0f, 260f, 120, 30), outline = .6f, washAlpha = 120)
    }

    private fun volcano(b: Burin) {
        b.wash(cone, 0xFF8E7A62.toInt(), 200)
        // Hachures de relief : des traits qui descendent du cratère, serrés sur le flanc à l'ombre.
        b.c.save(); b.c.clipPath(cone)
        val ink = b.pen(Ink.SEPIA, .55f)
        val rnd = Random(8)
        for (i in 0..70) {
            val f = i / 70f
            val sx = 150f + f * 64f
            val sy = 152f + 6f * kotlin.math.abs(f - .5f)
            val ex = 30f + f * 300f
            val ey = 300f
            ink.alpha = if (f > .55f) 255 else (90 + 160 * (1f - f / .55f) * .4f).toInt()
            val bend = (rnd.nextFloat() - .5f) * 14f
            b.c.drawLine(sx, sy, (sx + ex) / 2f + bend, (sy + ey) / 2f, ink)
            b.c.drawLine((sx + ex) / 2f + bend, (sy + ey) / 2f, ex, ey, ink)
        }
        b.c.restore()
        b.hatch(cone, -60f, 2.4f, .5f, Ink.SEPIA, Fade(300f, 260f, 200f, 200f, 230, 0))
        // Ravines sombres.
        for ((x0, x1) in listOf(160f to 92f, 176f to 150f, 204f to 250f, 210f to 296f)) {
            b.taper(x0, 162f, x0 + (x1 - x0) * .3f, 200f, x0 + (x1 - x0) * .7f, 240f, x1, 292f, 3.2f, withAlpha(Ink.SEPIA, 200))
        }
        // Le soufre : croûtes jaunes autour du cratère et des évents.
        sulfurCrust(b, 182f, 166f, 40f, 12f, 1)
        sulfurCrust(b, 128f, 218f, 16f, 7f, 2)
        sulfurCrust(b, 240f, 212f, 16f, 7f, 3)
        // L'intérieur du cratère.
        val crater = ellipse(182f, 157f, 17f, 4.5f)
        b.body(crater, 0xFF3A2A22.toInt(), .9f, 0f, outline = .8f, washAlpha = 230)
        b.stroke(cone, 1.2f)
        for ((x, y) in listOf(128f to 214f, 240f to 208f)) b.body(ellipse(x, y, 4f, 1.6f), 0xFF3A2A22.toInt(), 1f, 0f, outline = .6f)
    }

    /** Croûte de soufre : des plaques jaunes déchiquetées, facettées, autour d'un évent. */
    private fun sulfurCrust(b: Burin, x: Float, y: Float, rx: Float, ry: Float, seed: Int) {
        val rnd = Random(seed)
        b.c.save(); b.c.clipPath(cone)
        val patches = (rx / 4f).toInt() + 3
        for (i in 0 until patches) {
            val px = x + (rnd.nextFloat() - .5f) * rx * 2f
            val py = y + (rnd.nextFloat() - .25f) * ry * 1.6f
            val r = 2.6f + rnd.nextFloat() * 4.4f
            val n = 7 + rnd.nextInt(4)
            val pts = FloatArray(n * 2)
            for (k in 0 until n) {
                val a = k * 6.283f / n + rnd.nextFloat() * .4f
                val rr = r * (.55f + rnd.nextFloat() * .5f)
                pts[k * 2] = px + kotlin.math.cos(a) * rr; pts[k * 2 + 1] = py + kotlin.math.sin(a) * rr * .7f
            }
            val patch = poly(*pts)
            b.fill(patch, 0xFFF4D83A.toInt())
            // Une facette d'ombre sur le bas de chaque plaque.
            val h = n / 2
            b.fill(poly(pts[0], pts[1], px, py, pts[h * 2], pts[h * 2 + 1], pts[(h - 1) * 2], pts[(h - 1) * 2 + 1]), 0xFFD6A51E.toInt())
            b.stroke(patch, .5f, 0xFF7A560C.toInt())
        }
        b.c.restore()
    }

    private fun crystals(b: Burin) {
        // Le rocher qui porte les cristaux : un bloc irrégulier, modelé en hachures.
        val rock = Path().apply {
            moveTo(42f, 316f); lineTo(46f, 296f); cubicTo(52f, 282f, 70f, 276f, 88f, 279f)
            cubicTo(108f, 280f, 128f, 288f, 138f, 302f); lineTo(140f, 316f); close()
        }
        b.body(rock, 0xFF7C6E5E.toInt(), 0f, outline = 0f, washAlpha = 220)
        b.hatch(rock, 25f, 2f, .5f, Ink.SEPIA, Fade(140f, 316f, 70f, 280f, 255, 30))
        b.hatch(rock, -40f, 2.6f, .45f, Ink.SEPIA, Fade(140f, 316f, 110f, 296f, 230, 0))
        b.stroke(rock, 1.1f)
        // Une gerbe de cristaux en bipyramides, qui rayonne d'un même pied.
        cluster(b, 92f, 286f, 9, 31, 1f)
        cluster(b, 126f, 296f, 4, 32, .62f)
    }

    private fun cluster(b: Burin, x0: Float, y0: Float, n: Int, seed: Int, scale: Float) {
        val rnd = Random(seed)
        val list = List(n) { i ->
            val a = -160f + i * (140f / (n - 1)) + (rnd.nextFloat() - .5f) * 10f
            floatArrayOf(a, (20f + rnd.nextFloat() * 22f - kotlin.math.abs(a + 90f) * .12f) * scale, (4.6f + rnd.nextFloat() * 2.6f) * scale)
        }
        // Les plus couchés d'abord : ceux du milieu passent devant.
        for (c in list.sortedByDescending { kotlin.math.abs(it[0] + 90f) }) {
            val len = c[1]; val w = c[2]
            b.c.save(); b.c.translate(x0, y0); b.c.rotate(c[0])
            val light = poly(0f, 0f, len * .72f, -w, len, 0f, len * .72f, 0f)
            val dark = poly(0f, 0f, len * .72f, 0f, len, 0f, len * .72f, w)
            b.fill(light, 0xFFF8DE48.toInt())
            b.fill(dark, 0xFFD09A18.toInt())
            b.hatch(dark, 0f, 1.3f, .4f, 0xFF7A560C.toInt())
            b.line(len * .72f, -w, len * .72f, w, .5f, 0xFF7A560C.toInt())
            b.line(1f, -.5f, len * .72f, -w * .6f, .5f, 0xFFFFF6C0.toInt())
            b.stroke(poly(0f, 0f, len * .72f, -w, len, 0f, len * .72f, w), .65f, 0xFF4E3608.toInt())
            b.c.restore()
        }
    }

    // ───────────── animation ─────────────

    private val puff = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = RadialGradient(0f, 0f, 1f, intArrayOf(0x88FF9A3C.toInt(), 0x00FF9A3C), null, Shader.TileMode.CLAMP)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glow }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        glowPaint.alpha = (150 + 90 * sin(t * 2.2f)).toInt()
        c.save(); c.translate(182f, 156f); c.scale(40f, 22f); c.drawCircle(0f, 0f, 1f, glowPaint); c.restore()
        // Le panache : des bouffées qui montent, gonflent et dérivent vers la droite.
        plume(c, t, 182f, 156f, 12, 8f, 100f, 7f, 26f, 58f)
        plume(c, t + 1.3f, 128f, 212f, 5, 4.5f, 40f, 2.5f, 8f, 16f)
        plume(c, t + 2.6f, 240f, 206f, 5, 4.8f, 44f, 2.5f, 9f, 20f)
    }

    private fun plume(c: Canvas, t: Float, x: Float, y: Float, n: Int, period: Float, rise: Float, r0: Float, r1: Float, drift: Float) {
        // Des bouffées en chou-fleur : une ombre grise décalée, la masse claire, l'encre au sommet.
        for (i in n - 1 downTo 0) {
            val life = ((t / period + i / n.toFloat()) % 1f)
            val px = x + drift * life * life + 4f * sin(t * .8f + i * 1.7f) * life
            val py = y - rise * life
            val r = r0 + (r1 - r0) * kotlin.math.sqrt(life)
            val a = (if (life > .65f) (1f - life) / .35f else 1f) * (if (life < .06f) life / .06f else 1f)
            val sulfurous = (1f - life * 3f).coerceAtLeast(0f)
            for (lobe in 0..2) {
                val lx = px + (lobe - 1) * r * .55f
                val ly = py - (if (lobe == 1) r * .35f else 0f)
                val lr = r * (if (lobe == 1) .8f else .62f)
                puff.style = Paint.Style.FILL
                puff.color = withAlpha(0xFFB3AA9C.toInt(), (235 * a).toInt())
                c.drawCircle(lx + lr * .16f, ly + lr * .2f, lr, puff)
                puff.color = withAlpha(blend(0xFFF4EFE4.toInt(), 0xFFF2E08A.toInt(), sulfurous), (245 * a).toInt())
                c.drawCircle(lx - lr * .08f, ly - lr * .1f, lr * .9f, puff)
                puff.style = Paint.Style.STROKE; puff.strokeWidth = .6f
                puff.color = withAlpha(Ink.BROWN, (190 * a).toInt())
                c.drawArc(lx - lr, ly - lr, lx + lr, ly + lr, 195f, 130f, false, puff)
            }
        }
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        val g = f.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - g) + ((b shr s) and 0xFF) * g).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
}
