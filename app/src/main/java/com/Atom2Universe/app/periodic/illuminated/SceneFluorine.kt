package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fluor — la dent, en coupe de planche d'anatomie : émail, dentine, pulpe et ses vaisseaux,
 * gencive et os. Une brosse frotte, la mousse du dentifrice bouillonne, et les ions fluorure
 * vont se fixer dans l'émail, qui en sort plus dur.
 */
internal class SceneFluorine : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_tooth_brushing
    override val noteRes = R.string.card_note_tooth_brushing
    override val explainRes = R.string.card_explain_tooth_brushing

    private val crown = Path().apply {
        moveTo(140f, 202f); cubicTo(128f, 186f, 120f, 166f, 123f, 148f)
        cubicTo(125f, 132f, 136f, 120f, 148f, 120f); cubicTo(157f, 120f, 160f, 130f, 165f, 130f)
        cubicTo(170f, 130f, 174f, 116f, 182f, 116f); cubicTo(190f, 116f, 194f, 130f, 199f, 130f)
        cubicTo(204f, 130f, 207f, 120f, 214f, 120f); cubicTo(226f, 120f, 236f, 132f, 237f, 148f)
        cubicTo(240f, 166f, 232f, 186f, 220f, 202f); close()
    }
    private val roots = Path().apply {
        moveTo(141f, 196f); cubicTo(139f, 236f, 144f, 276f, 152f, 300f)
        cubicTo(158f, 306f, 166f, 300f, 166f, 290f); cubicTo(168f, 262f, 170f, 236f, 180f, 228f)
        cubicTo(190f, 236f, 192f, 262f, 194f, 290f); cubicTo(194f, 300f, 202f, 306f, 208f, 300f)
        cubicTo(216f, 276f, 221f, 236f, 219f, 196f); close()
    }
    private val dentinCrown = Path().apply {
        moveTo(146f, 202f); cubicTo(136f, 186f, 130f, 168f, 133f, 152f)
        cubicTo(135f, 140f, 142f, 132f, 150f, 131f); cubicTo(158f, 131f, 162f, 138f, 166f, 138f)
        cubicTo(171f, 138f, 176f, 127f, 182f, 127f); cubicTo(188f, 127f, 193f, 138f, 198f, 138f)
        cubicTo(202f, 138f, 206f, 131f, 212f, 131f); cubicTo(220f, 132f, 226f, 140f, 227f, 152f)
        cubicTo(230f, 168f, 224f, 186f, 214f, 202f); close()
    }
    private val pulp = Path().apply {
        moveTo(160f, 186f); cubicTo(158f, 172f, 156f, 158f, 158f, 148f)
        cubicTo(160f, 144f, 164f, 148f, 166f, 154f); cubicTo(172f, 160f, 188f, 160f, 194f, 154f)
        cubicTo(196f, 148f, 200f, 144f, 202f, 148f); cubicTo(204f, 158f, 202f, 172f, 200f, 186f)
        cubicTo(198f, 200f, 204f, 250f, 201f, 292f); cubicTo(200f, 296f, 198f, 296f, 197f, 292f)
        cubicTo(196f, 256f, 190f, 214f, 180f, 208f); cubicTo(170f, 214f, 164f, 256f, 163f, 292f)
        cubicTo(162f, 296f, 160f, 296f, 159f, 292f); cubicTo(156f, 250f, 162f, 200f, 160f, 186f); close()
    }

    override fun engrave(b: Burin) {
        b.glow(180f, 170f, 170f, 0xFFD8E6F0.toInt(), 120)
        val tooth = Path().apply { op(crown, roots, Path.Op.UNION) }
        jaw(b, tooth)
        // La dentine, puis l'émail par-dessus la couronne.
        val dentin = Path().apply { op(dentinCrown, roots, Path.Op.UNION) }
        b.wash(tooth, 0xFFF0DDB0.toInt(), 255)
        b.hatch(dentin, 100f, 2f, .4f, withAlpha(0xFFA88A50.toInt(), 200))
        b.hatch(dentin, 90f, 1.6f, .5f, Ink.SEPIA, Fade(222f, 0f, 190f, 0f, 200, 0))
        val enamel = Path().apply { op(crown, dentinCrown, Path.Op.DIFFERENCE) }
        b.wash(enamel, 0xFFF6F9FC.toInt(), 255)
        b.hatch(enamel, 80f, 1.3f, .4f, withAlpha(0xFF8AA0B8.toInt(), 200))
        b.hatch(enamel, 70f, 1.6f, .5f, Ink.SEPIA, Fade(238f, 0f, 206f, 0f, 210, 0))
        b.c.save(); b.c.clipRect(0f, 0f, 360f, 199f)
        b.stroke(dentinCrown, .6f, withAlpha(Ink.SEPIA, 180))
        b.c.restore()
        // La pulpe, avec son nerf et ses vaisseaux qui montent par les canaux.
        b.wash(pulp, 0xFFE07A72.toInt(), 255)
        b.stipple(pulp, 260, .5f, withAlpha(0xFF8A2A24.toInt(), 200), 3)
        b.c.save(); b.c.clipPath(pulp)
        for ((dx, color) in listOf(-1.4f to 0xFFB8302A.toInt(), 0f to 0xFFF0D060.toInt(), 1.4f to 0xFF3A5AA0.toInt())) {
            for (side in floatArrayOf(-1f, 1f)) {
                val vessel = Path().apply {
                    moveTo(180f + side * 19f + dx, 296f)
                    cubicTo(180f + side * 21f + dx, 250f, 180f + side * 18f + dx, 200f, 180f + side * 6f + dx, 178f)
                    cubicTo(180f + side * 2f, 168f, 180f + side * 16f + dx, 160f, 180f + side * 20f + dx, 150f)
                }
                b.stroke(vessel, .9f, color)
            }
        }
        b.c.restore()
        b.stroke(pulp, .7f)
        // Le cément qui gaine les racines, et le contour.
        b.c.save(); b.c.clipRect(0f, 206f, 360f, 340f)
        b.stroke(roots, 1.6f, withAlpha(0xFFE8D8B0.toInt(), 255))
        b.c.restore()
        b.stroke(tooth, 1.1f)
        b.c.drawOval(127f, 136f, 133f, 156f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200)))
    }

    /** La gencive et l'os de la mâchoire, en coupe, autour de la dent. */
    private fun jaw(b: Burin, tooth: Path) {
        val bone = Path().apply {
            moveTo(40f, 236f); cubicTo(80f, 232f, 120f, 238f, 136f, 226f)
            lineTo(224f, 226f); cubicTo(240f, 238f, 280f, 232f, 320f, 236f)
            lineTo(320f, 340f); lineTo(40f, 340f); close()
            op(tooth, Path.Op.DIFFERENCE)
        }
        b.wash(bone, 0xFFEAD8B4.toInt(), 255)
        b.stipple(bone, 1100, .55f, withAlpha(Ink.BROWN, 200), 5)
        val rnd = Random(4)
        b.c.save(); b.c.clipPath(bone)
        repeat(70) {
            val x = 44f + rnd.nextFloat() * 272f; val y = 246f + rnd.nextFloat() * 80f
            val r = 2f + rnd.nextFloat() * 4f
            b.c.drawOval(x - r, y - r * .7f, x + r, y + r * .7f, b.pen(withAlpha(0xFFC89A80.toInt(), 220)))
            b.c.drawOval(x - r, y - r * .7f, x + r, y + r * .7f, b.pen(Ink.BROWN, .5f))
        }
        b.c.restore()
        // L'os compact, en bord épais au sommet.
        val crest = Path().apply {
            moveTo(40f, 236f); cubicTo(80f, 232f, 120f, 238f, 136f, 226f)
            moveTo(224f, 226f); cubicTo(240f, 238f, 280f, 232f, 320f, 236f)
        }
        b.stroke(crest, 3f, 0xFFD8C090.toInt())
        b.stroke(crest, .8f)
        b.stroke(bone, .9f)
        // La gencive : rose, qui épouse le collet.
        val gum = Path().apply {
            moveTo(40f, 214f); cubicTo(80f, 210f, 112f, 214f, 126f, 196f)
            cubicTo(130f, 190f, 136f, 188f, 142f, 194f)
            lineTo(218f, 194f); cubicTo(224f, 188f, 230f, 190f, 234f, 196f)
            cubicTo(248f, 214f, 280f, 210f, 320f, 214f)
            lineTo(320f, 238f); cubicTo(280f, 234f, 240f, 240f, 224f, 228f)
            lineTo(136f, 228f); cubicTo(120f, 240f, 80f, 234f, 40f, 238f); close()
            op(tooth, Path.Op.DIFFERENCE)
        }
        b.wash(gum, 0xFFE89A96.toInt(), 255)
        b.hatch(gum, 20f, 1.8f, .45f, withAlpha(0xFF8A3A36.toInt(), 200), Fade(0f, 238f, 0f, 200f, 220, 40))
        b.stroke(gum, .9f)
    }

    override fun sprites(): List<Sprite> = listOf(Sprite(BRUSH, RectF(120f, 40f, 340f, 116f)) { b -> brush(b) })

    /** La brosse à dents, posée en travers de la couronne, le manche vers le haut à droite. */
    private fun brush(b: Burin) {
        val handle = Path().apply {
            moveTo(228f, 90f); cubicTo(250f, 84f, 280f, 70f, 340f, 52f)
            lineTo(344f, 64f); cubicTo(290f, 80f, 258f, 96f, 232f, 102f); close()
        }
        b.body(handle, 0xFF3F7FC0.toInt(), .3f, 70f, Fade(0f, 102f, 0f, 84f, 230, 0), outline = 1f, washAlpha = 255)
        b.stroke(Path().apply { moveTo(240f, 92f); cubicTo(262f, 86f, 290f, 74f, 330f, 60f) }, 1.4f, withAlpha(0xFFFFFFFF.toInt(), 180))
        val head = Path().apply { addRoundRect(136f, 88f, 236f, 102f, 6f, 6f, Path.Direction.CW) }
        b.body(head, 0xFF3F7FC0.toInt(), .3f, 0f, Fade(0f, 102f, 0f, 90f, 230, 0), outline = 1f, washAlpha = 255)
        // Les touffes de poils, blanches et bleues, en rangées.
        for (i in 0 until 12) {
            val x = 141f + i * 8f
            val blue = i % 3 == 1
            val tuft = rect(x - 2.6f, 102f, x + 2.6f, 114f)
            b.wash(tuft, if (blue) 0xFF7AB8E8.toInt() else 0xFFF8F8F4.toInt(), 255)
            b.hatch(tuft, 90f, 1f, .35f, withAlpha(Ink.SEPIA, 180))
            b.stroke(tuft, .5f)
        }
    }

    // ───────────── animation ─────────────

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La brosse frotte d'avant en arrière.
        val scrub = 9f * sin(t * 2f * PI.toFloat() / .62f)
        s.draw(c, BRUSH, dx = scrub, dy = 3.5f + 1.2f * sin(t * 2f * PI.toFloat() / .31f))

        // Les ions fluorure : ils quittent la mousse et vont se loger dans l'émail.
        for (i in 0 until IONS) {
            val p = ((t * .45f + i * .137f) % 1f)
            val x = ION_X[i]
            val y0 = 112f; val y1 = ION_Y[i]
            val y = y0 + (y1 - y0) * p
            val a = when { p < .15f -> p / .15f; p > .85f -> (1f - p) / .15f; else -> 1f }
            fill.color = withAlpha(0xFFB8E04A.toInt(), (230 * a).toInt())
            c.drawCircle(x + 1.5f * sin(t * 4f + i), y, 2.2f, fill)
            ink.color = withAlpha(0xFF4A6A1A.toInt(), (230 * a).toInt()); ink.strokeWidth = .5f
            c.drawCircle(x + 1.5f * sin(t * 4f + i), y, 2.2f, ink)
            // À l'arrivée, l'émail brille un instant.
            if (p > .8f) {
                val g = (p - .8f) / .2f
                ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * (1f - g)).toInt()); ink.strokeWidth = .8f
                val r = 2f + g * 5f
                c.drawLine(x - r, y1, x + r, y1, ink); c.drawLine(x, y1 - r, x, y1 + r, ink)
            }
        }

        // La mousse : des bulles qui naissent entre les poils, gonflent et éclatent.
        for (i in 0 until BUBBLES) {
            val period = 1.4f + (i % 4) * .3f
            val p = ((t + i * .37f) % period) / period
            val x = BUBBLE_X[i] + scrub * .5f + 3f * sin(t * 2f + i)
            val y = 118f - p * 16f - (i % 3) * 3f
            val r = (1.2f + p * 2.8f) * (if (i % 5 == 0) 1.5f else 1f)
            val a = if (p > .85f) (1f - p) / .15f else 1f
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (200 * a).toInt())
            c.drawCircle(x, y, r, fill)
            ink.color = withAlpha(0xFF8AA0B8.toInt(), (220 * a).toInt()); ink.strokeWidth = .5f
            c.drawCircle(x, y, r, ink)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (255 * a).toInt())
            c.drawCircle(x - r * .35f, y - r * .35f, r * .25f, fill)
        }

        // Un éclat sur l'émail, de temps en temps.
        val glint = (t % 2.6f) / 2.6f
        if (glint < .3f) {
            val g = sin(glint / .3f * PI.toFloat())
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * g).toInt()); ink.strokeWidth = 1f
            val r = 7f * g
            c.drawLine(130f - r, 146f, 130f + r, 146f, ink); c.drawLine(130f, 146f - r, 130f, 146f + r, ink)
            ink.strokeWidth = .7f
            c.drawLine(130f - r * .5f, 146f - r * .5f, 130f + r * .5f, 146f + r * .5f, ink)
            c.drawLine(130f - r * .5f, 146f + r * .5f, 130f + r * .5f, 146f - r * .5f, ink)
        }
    }

    private companion object {
        const val BRUSH = 1
        const val IONS = 8
        const val BUBBLES = 16
        val ION_X = floatArrayOf(140f, 152f, 166f, 178f, 190f, 202f, 214f, 226f)
        /** La profondeur où chaque ion s'arrête : juste sous la surface de l'émail. */
        val ION_Y = floatArrayOf(128f, 124f, 134f, 121f, 124f, 134f, 124f, 130f)
        val BUBBLE_X = FloatArray(BUBBLES).also { a ->
            val rnd = Random(9)
            for (i in a.indices) a[i] = 136f + rnd.nextFloat() * 96f
        }
    }
}
