package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Manganèse — la pile de la lampe de poche. Dans les piles alcalines, c'est le dioxyde de
 * manganèse qui fournit le courant. Un grenier, la nuit : le faisceau de la lampe balaie la
 * pièce et fait sortir du noir la malle, le globe, le tableau, et le chat dont les yeux
 * s'allument ; la poussière danse dans la lumière.
 */
internal class SceneManganese : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_flashlight
    override val noteRes = R.string.card_note_flashlight
    override val explainRes = R.string.card_explain_flashlight

    override fun engrave(b: Burin) {
        room(b, lit = false)
        // Le clair de lune qui tombe de l'œil-de-bœuf.
        b.fill(poly(166f, 98f, 196f, 98f, 236f, 300f, 150f, 300f), withAlpha(0xFF6A88C8.toInt(), 28))
    }

    /** Le grenier entier ; [lit] le donne en pleine lumière, sinon presque noir sous la lune. */
    private fun room(b: Burin, lit: Boolean) {
        fun k(color: Int) = if (lit) color else dim(color)
        // Le mur de planches.
        val wall = rect(40f, 50f, 320f, FLOOR)
        b.wash(wall, k(0xFF9A7650.toInt()), 255)
        var x = 40f
        while (x < 320f) { b.line(x, 50f, x, FLOOR, .7f, k(0xFF4A3220.toInt())); x += 18f }
        b.hatchRect(RectF(40f, 50f, 320f, FLOOR), 90f, 2.2f, .4f, Ink.SEPIA, Fade(0f, 60f, 0f, FLOOR, 120, 200))
        // L'œil-de-bœuf et la lune.
        val win = ellipse(181f, 100f, 18f, 18f)
        b.wash(win, if (lit) 0xFF2A3A6A.toInt() else 0xFF1E2A4E.toInt(), 255)
        b.c.drawCircle(186f, 95f, 6f, b.pen(0xFFF4ECC8.toInt()))
        b.c.drawCircle(189f, 93f, 5.4f, b.pen(if (lit) 0xFF2A3A6A.toInt() else 0xFF1E2A4E.toInt()))
        b.c.drawCircle(181f, 100f, 18f, b.pen(k(0xFF6A4A2A.toInt()), 4f))
        b.line(163f, 100f, 199f, 100f, 1.6f, k(0xFF6A4A2A.toInt())); b.line(181f, 82f, 181f, 118f, 1.6f, k(0xFF6A4A2A.toInt()))
        // Les chevrons du toit, et l'entrait.
        for (beam in listOf(poly(40f, 150f, 40f, 132f, 132f, 50f, 152f, 50f), poly(320f, 150f, 320f, 132f, 228f, 50f, 208f, 50f))) {
            b.body(beam, k(0xFF6A4A2E.toInt()), .45f, 30f, outline = 1f, washAlpha = 255)
        }
        b.body(rect(40f, 70f, 320f, 80f), k(0xFF6A4A2E.toInt()), .45f, 0f, outline = 1f, washAlpha = 255)
        // Une toile d'araignée dans l'angle.
        val web = b.pen(withAlpha(k(0xFFE8E8E4.toInt()), 200), .4f)
        for (a in 0..5) {
            val r = a * PI.toFloat() / 10f
            b.c.drawLine(48f, 140f, 48f + cos(-r) * 34f, 140f + sin(-r) * 34f, web)
        }
        for (r in floatArrayOf(10f, 18f, 26f)) b.c.drawArc(48f - r, 140f - r, 48f + r, 140f + r, -90f, 90f, false, web)
        // Le plancher, en perspective.
        val floor = rect(40f, FLOOR, 320f, 340f)
        b.wash(floor, k(0xFF7A5A3A.toInt()), 255)
        b.hatchRect(RectF(40f, FLOOR, 320f, 340f), 0f, 2f, .4f, Ink.SEPIA, Fade(0f, FLOOR, 0f, 330f, 200, 120))
        for (i in -6..6) b.line(180f + i * 9f, FLOOR, 180f + i * 40f, 340f, .7f, k(0xFF3A2614.toInt()))
        b.line(40f, FLOOR, 320f, FLOOR, 1f)
        // Le tableau posé contre le mur.
        val frame = poly(276f, 236f, 282f, 150f, 318f, 152f, 316f, 238f)
        b.body(frame, k(0xFFB88A3A.toInt()), .3f, 0f, outline = 1f, washAlpha = 255)
        val canvas = poly(282f, 230f, 287f, 158f, 312f, 160f, 310f, 231f)
        b.wash(canvas, k(0xFF8AA8C0.toInt()), 255)
        b.fill(poly(282f, 230f, 286f, 196f, 298f, 186f, 310f, 200f, 310f, 231f), k(0xFF5A7A4A.toInt()))
        b.c.drawCircle(300f, 172f, 5f, b.pen(k(0xFFF2D27A.toInt())))
        b.stroke(canvas, .7f)
        // Le globe sur son pied.
        b.line(104f, 248f, 104f, 216f, 2.4f, k(0xFF6A4A2E.toInt()))
        b.body(ellipse(104f, 249f, 14f, 4f), k(0xFF6A4A2E.toInt()), .3f, 0f, outline = .8f, washAlpha = 255)
        val globe = ellipse(104f, 194f, 21f, 21f)
        b.wash(globe, k(0xFF4A7AA8.toInt()), 255)
        b.c.save(); b.c.clipPath(globe)
        for (land in listOf(poly(90f, 180f, 100f, 176f, 106f, 186f, 98f, 200f, 92f, 196f), poly(108f, 188f, 120f, 184f, 122f, 204f, 112f, 210f))) {
            b.fill(land, k(0xFF8AA05A.toInt()))
        }
        b.c.restore()
        b.hatch(globe, 40f, 1.8f, .4f, Ink.SEPIA, Fade(124f, 214f, 98f, 186f, 220, 0))
        b.stroke(globe, 1f)
        b.c.drawArc(81f, 171f, 127f, 217f, -120f, 240f, false, b.pen(k(0xFFC8A04A.toInt()), 1.8f))
        // La pile de livres.
        for ((i, col) in listOf(0xFF7A2A22.toInt(), 0xFF2A4A6A.toInt(), 0xFF5A6A2A.toInt()).withIndex()) {
            val y = 252f - i * 7f
            b.body(rect(136f + i * 2f, y - 7f, 172f - i * 3f, y), k(col), .3f, 0f, outline = .7f, washAlpha = 255)
            b.line(138f + i * 2f, y - 3.5f, 170f - i * 3f, y - 3.5f, .5f, k(0xFFE8D8B0.toInt()))
        }
        // La malle bombée, cerclée de laiton.
        val trunk = rect(196f, 226f, 274f, 268f)
        b.body(trunk, k(0xFF6A3E22.toInt()), .45f, 90f, outline = 1f, washAlpha = 255)
        val lid = Path().apply { moveTo(196f, 226f); cubicTo(198f, 208f, 272f, 208f, 274f, 226f); close() }
        b.body(lid, k(0xFF7A4A2A.toInt()), .35f, 90f, outline = 1f, washAlpha = 255)
        for (x2 in floatArrayOf(208f, 262f)) b.body(rect(x2 - 3f, 213f, x2 + 3f, 268f), k(0xFFC8A04A.toInt()), .3f, 0f, outline = .6f, washAlpha = 255)
        b.body(rect(230f, 224f, 240f, 236f), k(0xFFC8A04A.toInt()), .3f, 0f, outline = .7f, washAlpha = 255)
        // Le chat assis sur la malle.
        val cat = Path().apply {
            addOval(CAT_X - 13f, 186f, CAT_X + 13f, 216f, Path.Direction.CW)
            addCircle(CAT_X + 2f, 182f, 9f, Path.Direction.CW)
        }
        val catShape = Path().apply {
            op(cat, poly(CAT_X - 6f, 178f, CAT_X - 3f, 168f, CAT_X + 2f, 176f, CAT_X + 4f, 176f, CAT_X + 8f, 167f, CAT_X + 10f, 179f), Path.Op.UNION)
        }
        b.body(catShape, k(0xFF3A3A40.toInt()), .5f, 60f, outline = 1f, washAlpha = 255)
        b.stroke(Path().apply { moveTo(CAT_X - 12f, 212f); cubicTo(CAT_X - 30f, 214f, CAT_X - 30f, 196f, CAT_X - 22f, 192f) }, 4f, k(0xFF3A3A40.toInt()))
        b.c.drawCircle(EYE_X, EYE_Y, 1.6f, b.pen(k(0xFFB8C850.toInt())))
        b.c.drawCircle(EYE_X + 6f, EYE_Y, 1.6f, b.pen(k(0xFFB8C850.toInt())))
        // Deux piles neuves, tombées par terre.
        for ((bx, by) in listOf(126f to 286f, 140f to 292f)) {
            val cell = rect(bx, by - 4f, bx + 22f, by + 4f)
            b.body(cell, k(0xFF2A2A2E.toInt()), .3f, 0f, outline = .7f, washAlpha = 255)
            b.fill(rect(bx + 14f, by - 4f, bx + 22f, by + 4f), k(0xFFC8742A.toInt()))
            b.fill(rect(bx + 22f, by - 1.6f, bx + 24f, by + 1.6f), k(0xFFC8CCD0.toInt()))
            b.stroke(cell, .7f)
        }
    }

    /** La couleur dans le noir : presque éteinte, tirant vers le bleu de la nuit. */
    private fun dim(color: Int): Int {
        val a = .16f
        val r = ((color shr 16) and 0xFF) * a + 0x0C * (1f - a)
        val g = ((color shr 8) and 0xFF) * a + 0x0E * (1f - a)
        val bl = (color and 0xFF) * a + 0x1A * (1f - a)
        return (color and 0xFF000000.toInt()) or (r.toInt() shl 16) or (g.toInt() shl 8) or bl.toInt()
    }

    override fun sprites(): List<Sprite> = listOf(
        Sprite(LIT, RectF(40f, 50f, 320f, 340f)) { b -> room(b, lit = true) },
        Sprite(TORCH, RectF(-4f, -14f, 76f, 14f)) { b ->
            val body = rect(0f, -7f, 56f, 7f)
            b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, -7f, 0f, 7f,
                intArrayOf(0xFFE05A44.toInt(), 0xFFA8302A.toInt(), 0xFF5A1814.toInt()), null, Shader.TileMode.CLAMP)
            b.c.drawPath(body, b.p)
            b.stroke(body, .8f)
            for (x in floatArrayOf(10f, 16f, 22f)) b.line(x, -7f, x, 7f, .6f, 0xFF3A0E0A.toInt())
            val head = poly(56f, -7f, 72f, -11f, 72f, 11f, 56f, 7f)
            b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, -11f, 0f, 11f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFF8A9096.toInt(), 0xFFE8ECEE.toInt(), 0xFF3A3E44.toInt()), null, Shader.TileMode.CLAMP)
            b.c.drawPath(head, b.p)
            b.stroke(head, .8f)
            b.body(rect(40f, -9f, 48f, -7f), 0xFF2A2A2E.toInt(), 0f, outline = .5f, washAlpha = 255)
            b.fill(ellipse(72f, 0f, 2f, 10f), 0xFFFFF8D8.toInt())
        },
        Sprite(GLOW, RectF(-50f, -50f, 50f, 50f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(0f, 0f, 50f,
                intArrayOf(0xAAFFF0C8.toInt(), 0x44FFE0A0, 0x00FFE0A0), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(0f, 0f, 50f, b.p)
        }
    )

    // ───────────── animation ─────────────

    private val cone = Path()
    private val spot = Path()
    private val haze = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val motes = FloatArray(MOTES * 3).also { m ->
        val rnd = Random(17)
        for (k in 0 until MOTES) { m[k * 3] = 60f + rnd.nextFloat() * 250f; m[k * 3 + 1] = 70f + rnd.nextFloat() * 200f; m[k * 3 + 2] = rnd.nextFloat() * 6.3f }
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Où vise la lampe : un balayage lent, qui s'attarde un peu partout.
        val tx = 196f + 96f * sin(t * .42f) + 14f * sin(t * 1.1f)
        val ty = 176f + 34f * sin(t * .31f + 1f)
        val aim = atan2(ty - PIVOT_Y, tx - PIVOT_X)
        val lx = PIVOT_X + cos(aim) * LENS; val ly = PIVOT_Y + sin(aim) * LENS
        val dir = atan2(ty - ly, tx - lx)
        // Le cône de lumière, et la tache plus vive au bout.
        val nx = -sin(dir); val ny = cos(dir)
        val a1 = dir - HALF; val a2 = dir + HALF
        cone.reset()
        cone.moveTo(lx + nx * 9f, ly + ny * 9f)
        cone.lineTo(lx + cos(a2) * 460f, ly + sin(a2) * 460f)
        cone.lineTo(lx + cos(a1) * 460f, ly + sin(a1) * 460f)
        cone.lineTo(lx - nx * 9f, ly - ny * 9f)
        cone.close()
        val d = hypot(tx - lx, ty - ly)
        val r = d * .24f
        spot.reset(); spot.addOval(tx - r, ty - r * .86f, tx + r, ty + r * .86f, Path.Direction.CW)
        c.save(); c.clipPath(cone)
        s.draw(c, LIT, alpha = 110)
        c.clipPath(spot)
        s.draw(c, LIT, alpha = 255)
        c.restore()
        c.save(); c.clipPath(cone)
        haze.color = withAlpha(0xFFFFF0C8.toInt(), 26)
        c.drawPath(cone, haze)
        c.translate(tx, ty); c.scale(r / 40f, r / 40f)
        s.draw(c, GLOW, alpha = 150)
        c.restore()
        // La poussière qui danse dans le faisceau.
        for (k in 0 until MOTES) {
            val mx = motes[k * 3] + 6f * sin(t * .3f + motes[k * 3 + 2])
            val my = motes[k * 3 + 1] + 5f * sin(t * .23f + motes[k * 3 + 2] * 1.7f)
            val off = abs(angleDiff(atan2(my - ly, mx - lx), dir))
            if (off > HALF) continue
            fill.color = withAlpha(0xFFFFF4D8.toInt(), (220 * (1f - off / HALF)).toInt())
            c.drawCircle(mx, my, .8f, fill)
        }
        // Les yeux du chat brillent quand la lumière les touche.
        val eye = abs(angleDiff(atan2(EYE_Y - ly, EYE_X + 3f - lx), dir))
        if (eye < HALF) {
            val k = 1f - eye / HALF
            fill.color = withAlpha(0xFFE8FF80.toInt(), (255 * k).toInt())
            c.drawCircle(EYE_X, EYE_Y, 2f, fill); c.drawCircle(EYE_X + 6f, EYE_Y, 2f, fill)
            fill.color = withAlpha(0xFFE8FF80.toInt(), (90 * k).toInt())
            c.drawCircle(EYE_X, EYE_Y, 5f, fill); c.drawCircle(EYE_X + 6f, EYE_Y, 5f, fill)
        }
        // La lampe, et la lueur autour de sa vitre.
        c.save(); c.translate(lx, ly); c.scale(.5f, .5f); s.draw(c, GLOW, alpha = 220); c.restore()
        c.save(); c.translate(PIVOT_X, PIVOT_Y); c.rotate(Math.toDegrees(aim.toDouble()).toFloat())
        s.draw(c, TORCH)
        c.restore()
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = a - b
        while (d > PI) d -= 2f * PI.toFloat()
        while (d < -PI) d += 2f * PI.toFloat()
        return d
    }

    private companion object {
        const val FLOOR = 250f
        const val CAT_X = 236f
        const val EYE_X = 235f
        const val EYE_Y = 181f
        const val PIVOT_X = 54f
        const val PIVOT_Y = 330f
        const val LENS = 72f
        const val HALF = .2f
        const val MOTES = 26
        const val LIT = 1
        const val TORCH = 2
        const val GLOW = 3
    }
}
