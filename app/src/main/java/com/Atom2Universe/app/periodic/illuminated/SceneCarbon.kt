package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Carbone — l'arbre (direction approuvée, feuillage vert), gravé comme un chêne de planche
 * botanique. Le sol est vu en coupe : les racines, puis une veine de houille où reste
 * l'empreinte d'une fougère — le carbone du vivant devenu charbon.
 */
internal class SceneCarbon : EngravedScene() {

    override val nameRes = R.string.card_scene_oak_coal
    override val noteRes = R.string.card_note_oak_coal
    override val explainRes = R.string.card_explain_oak_coal

    override fun engrave(b: Burin) {
        underground(b)
        trunk(b)
    }

    private fun underground(b: Burin) {
        // L'herbe au ras du sol.
        val turf = rect(40f, GROUND - 4f, 320f, GROUND + 6f)
        b.body(turf, 0xFF6E9A4C.toInt(), 0f, outline = 0f, washAlpha = 170)
        val grass = b.pen(Ink.SEPIA, .5f)
        val rnd = Random(12)
        for (i in 0 until 120) {
            val x = 40f + i * 2.35f
            b.c.drawLine(x, GROUND + 2f, x + (rnd.nextFloat() - .5f) * 3f, GROUND - 3f - rnd.nextFloat() * 5f, grass)
        }
        // La terre.
        val soil = rect(40f, GROUND + 4f, 320f, SEAM)
        b.body(soil, 0xFF9C7449.toInt(), 0f, outline = 0f, washAlpha = 150)
        b.stipple(soil, 520, .55f, seed = 3)
        b.hatch(soil, 0f, 3f, .4f, Ink.SEPIA, Fade(0f, GROUND, 0f, SEAM, 40, 170))
        // Quelques cailloux.
        for (i in 0 until 9) {
            val x = 52f + rnd.nextFloat() * 256f; val y = GROUND + 18f + rnd.nextFloat() * 30f
            val stone = ellipse(x, y, 4f + rnd.nextFloat() * 4f, 2.5f + rnd.nextFloat() * 2f)
            b.body(stone, 0xFFB8AA90.toInt(), .45f, 30f, Fade(x + 6f, y + 4f, x - 4f, y - 3f, 255, 0), outline = .6f)
        }
        // La veine de houille, noire et feuilletée.
        val seam = Path().apply {
            moveTo(40f, SEAM); cubicTo(120f, SEAM - 6f, 220f, SEAM + 6f, 320f, SEAM - 2f)
            lineTo(320f, 340f); lineTo(40f, 340f); close()
        }
        b.body(seam, 0xFF2C2622.toInt(), 0f, outline = 0f, washAlpha = 230)
        b.hatch(seam, -4f, 1.6f, .5f, 0xFF0F0C0A.toInt())
        for (k in 0..3) {
            val y = SEAM + 6f + k * 6f
            b.c.drawLine(40f, y, 320f, y - 2f + (k % 2) * 3f, b.pen(withAlpha(0xFF8C8478.toInt(), 160), .5f))
        }
        b.stroke(seam, 1f)
        // L'empreinte d'une fronde de fougère dans le charbon.
        val fern = b.pen(withAlpha(0xFFB9AE98.toInt(), 220), .7f)
        val fx = 236f; val fy = SEAM + 14f
        b.c.drawLine(fx - 40f, fy + 4f, fx + 38f, fy - 4f, fern)
        for (i in 0 until 12) {
            val x = fx - 36f + i * 6.4f; val y = fy + 3.6f - i * .7f
            val len = 9f - kotlin.math.abs(i - 5) * .9f
            b.c.drawLine(x, y, x + 3f, y - len, fern); b.c.drawLine(x, y, x + 3f, y + len * .9f, fern)
        }
        // Les racines, effilées, qui fouillent la terre.
        val roots = listOf(
            floatArrayOf(176f, GROUND, 150f, 248f, 120f, 250f, 86f, 266f),
            floatArrayOf(178f, GROUND, 168f, 256f, 146f, 266f, 132f, 276f),
            floatArrayOf(184f, GROUND, 196f, 254f, 222f, 260f, 248f, 274f),
            floatArrayOf(186f, GROUND, 214f, 244f, 250f, 246f, 284f, 258f),
            floatArrayOf(180f, GROUND, 182f, 258f, 176f, 268f, 180f, 278f)
        )
        for (r in roots) b.taper(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], 6f, 0xFF4A3424.toInt())
        for (r in roots) {
            b.taper(r[6], r[7], r[6] - 6f, r[7] + 4f, r[6] - 10f, r[7] + 6f, r[6] - 16f, r[7] + 10f, 1.6f, 0xFF4A3424.toInt())
        }
    }

    private fun trunk(b: Burin) {
        val trunk = Path().apply {
            moveTo(164f, GROUND + 2f)
            cubicTo(172f, 214f, 170f, 186f, 168f, 162f)
            lineTo(192f, 162f)
            cubicTo(190f, 180f, 188f, 214f, 198f, GROUND + 2f)
            close()
        }
        b.body(trunk, 0xFF8A6440.toInt(), 0f, outline = 0f, washAlpha = 200)
        // L'écorce : des traits verticaux ondulés, serrés à l'ombre.
        val bark = b.pen(Ink.SEPIA, .6f)
        b.c.save(); b.c.clipPath(trunk)
        for (i in 0 until 14) {
            val x = 164f + i * 2.4f
            val path = Path().apply {
                moveTo(x, GROUND + 2f)
                var y = GROUND + 2f
                while (y > 148f) { y -= 6f; lineTo(x + sin(y * .3f + i) * 1.2f, y) }
            }
            bark.alpha = if (i > 8) 255 else 120
            b.c.drawPath(path, bark)
        }
        b.hatchRect(RectF(182f, 148f, 200f, GROUND + 2f), 90f, 1.4f, .5f)
        b.c.restore()
        b.stroke(trunk, 1.1f)
    }

    override fun sprites() = listOf(Sprite(CROWN, RectF(70f, 58f, 290f, 208f)) { b -> b.c.translate(0f, 12f); crown(b) })

    private val clumps = listOf(
        floatArrayOf(118f, 142f, 30f, 0f), floatArrayOf(244f, 140f, 31f, 0f),
        floatArrayOf(150f, 104f, 34f, 0f), floatArrayOf(214f, 102f, 34f, 0f),
        floatArrayOf(180f, 80f, 34f, 0f),
        floatArrayOf(148f, 150f, 30f, 1f), floatArrayOf(212f, 152f, 30f, 1f),
        floatArrayOf(180f, 126f, 36f, 1f)
    )

    private fun crown(b: Burin) {
        // Les branches maîtresses, visibles entre les masses de feuillage.
        b.taper(180f, 160f, 172f, 140f, 150f, 128f, 128f, 132f, 7f, 0xFF6E4E32.toInt())
        b.taper(180f, 160f, 190f, 136f, 214f, 124f, 238f, 128f, 7f, 0xFF6E4E32.toInt())
        b.taper(180f, 160f, 178f, 130f, 182f, 104f, 178f, 86f, 7f, 0xFF6E4E32.toInt())
        for ((i, k) in clumps.withIndex()) clump(b, k[0], k[1], k[2], k[3] > 0f, i)
    }

    /** Une masse de feuillage : contour festonné, lavis vert, ombre hachée en bas à droite. */
    private fun clump(b: Burin, x: Float, y: Float, r: Float, front: Boolean, seed: Int) {
        val rnd = Random(seed + 20)
        val shape = Path()
        val n = 13
        for (i in 0..n) {
            val a = i * 2 * PI.toFloat() / n
            val rr = r * (.92f + rnd.nextFloat() * .14f)
            val px = x + cos(a) * rr; val py = y + sin(a) * rr * .8f
            if (i == 0) shape.moveTo(px, py) else {
                val am = (i - .5f) * 2 * PI.toFloat() / n
                shape.quadTo(x + cos(am) * rr * 1.18f, y + sin(am) * rr * .8f * 1.18f, px, py)
            }
        }
        shape.close()
        b.fill(shape, PAPER)
        b.wash(shape, if (front) 0xFF6FA552.toInt() else 0xFF4E7F3C.toInt(), 200)
        b.washGradient(shape, 0xFFC8E39A.toInt(), x - r, y - r, 170, x + r * .2f, y + r * .1f, 0)
        b.hatch(shape, 50f, 2.2f, .55f, Ink.SEPIA, Fade(x + r * .8f, y + r * .7f, x - r * .1f, y - r * .2f, 240, 0))
        if (!front) b.hatch(shape, -30f, 2.8f, .45f, Ink.SEPIA, Fade(x + r, y + r, x, y, 200, 0))
        // De petites feuilles en V qui font le grain du feuillage.
        val leaf = b.pen(withAlpha(Ink.SEPIA, 190), .5f)
        b.c.save(); b.c.clipPath(shape)
        for (i in 0 until (r * 1.1f).toInt()) {
            val lx = x - r + rnd.nextFloat() * 2 * r; val ly = y - r * .8f + rnd.nextFloat() * 1.6f * r
            b.c.drawLine(lx - 2f, ly - 1.6f, lx, ly, leaf); b.c.drawLine(lx, ly, lx + 2f, ly - 1.6f, leaf)
        }
        b.c.restore()
        b.stroke(shape, .9f)
    }

    // ───────────── animation ─────────────

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // L'arbre ploie doucement au vent, depuis le pied.
        val sway = 1.1f * sin(t * .9f) + .4f * sin(t * 2.3f)
        s.draw(c, CROWN, deg = sway, px = 180f, py = GROUND)
        // Feuilles qui tombent en tournoyant.
        for (i in 0 until 4) {
            val period = 6f + i * 1.3f
            val life = ((t + i * 2.1f) % period) / period
            val x0 = 110f + i * 44f
            val x = x0 + 26f * life + 9f * sin(life * 12f + i)
            val y = 150f + life * (GROUND - 150f)
            val a = sin(life * 10f + i) * 70f
            c.save(); c.translate(x, y); c.rotate(a)
            leafPaint.style = Paint.Style.FILL
            leafPaint.color = withAlpha(if (i % 2 == 0) 0xFF8DB85E.toInt() else 0xFFC9A646.toInt(), (255 * (1f - life * life)).toInt())
            c.drawOval(-3.2f, -1.5f, 3.2f, 1.5f, leafPaint)
            leafPaint.style = Paint.Style.STROKE; leafPaint.strokeWidth = .45f
            leafPaint.color = withAlpha(Ink.SEPIA, (255 * (1f - life * life)).toInt())
            c.drawOval(-3.2f, -1.5f, 3.2f, 1.5f, leafPaint)
            c.restore()
        }
    }

    private companion object {
        const val GROUND = 236f
        const val SEAM = 288f
        const val CROWN = 1
        const val PAPER = 0xFFF6EDD8.toInt()
    }
}
