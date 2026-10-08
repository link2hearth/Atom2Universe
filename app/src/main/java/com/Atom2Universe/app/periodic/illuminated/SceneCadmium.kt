package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cadmium — le jaune du peintre. Les sels de cadmium donnent les jaunes, les orangés et les rouges
 * les plus éclatants des tubes de peinture. Au couchant, dans un champ de tournesols, un peintre
 * vu de dos pose des touches sur sa toile : le paysage derrière est gravé, sourd ; sur la toile,
 * le même paysage éclate en jaune, orange et rouge de cadmium. Chaque touche fraîche brille un
 * moment, puis sèche et se fond dans la peinture.
 */
internal class SceneCadmium : EngravedScene() {

    override val sky = Sky.DUSK
    override val nameRes = R.string.card_scene_painter_yellow
    override val noteRes = R.string.card_note_painter_yellow
    override val explainRes = R.string.card_explain_painter_yellow

    override fun engrave(b: Burin) {
        // Le vrai paysage, sourd : le soleil bas, les collines, le champ de tournesols.
        b.glow(82f, 160f, 60f, 0xFFF0A040.toInt(), 150)
        b.c.drawCircle(82f, 160f, 13f, b.pen(0xFFF8D890.toInt()))
        val hills = Path().apply {
            moveTo(40f, 178f); cubicTo(90f, 164f, 130f, 170f, 170f, 176f); cubicTo(220f, 182f, 270f, 160f, 320f, 170f)
            lineTo(320f, 190f); lineTo(40f, 190f); close()
        }
        b.body(hills, 0xFF6A7090.toInt(), .3f, 0f, outline = .7f, washAlpha = 220)
        val field = rect(40f, 184f, 320f, 340f)
        b.wash(field, 0xFF6A7A3A.toInt(), 255)
        b.hatchRect(RectF(40f, 184f, 320f, 340f), 0f, 2.4f, .4f, 0xFF3A4420.toInt(), Fade(0f, 184f, 0f, 330f, 80, 200))
        val rnd = Random(48)
        for (row in 0 until 9) {
            val y = 188f + row * row * 1.9f + row * 4f
            val r = 1.2f + row * .7f
            var x = 40f + rnd.nextFloat() * r * 3f
            while (x < 320f) {
                b.c.drawCircle(x, y, r * 1.5f, b.pen(withAlpha(0xFFD8A830.toInt(), 220)))
                b.c.drawCircle(x, y, r * .6f, b.pen(0xFF4A2A10.toInt()))
                x += r * 3.6f + rnd.nextFloat() * r * 1.5f
            }
        }
        // Le chevalet : deux pieds en A, le mât derrière la toile, la tablette, la pince du haut.
        b.body(limb(EASEL_X, 78f, 160f, 320f, 5f, 6f), 0xFF8A5A34.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(limb(EASEL_X, 78f, 254f, 320f, 5f, 6f), 0xFF8A5A34.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        // La toile : le même paysage, en couleurs de cadmium, à grandes touches.
        val canvas = rect(CL, CT, CR, CB)
        b.c.save(); b.c.clipPath(canvas)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, CT, 0f, HORIZON,
            intArrayOf(0xFFC8281E.toInt(), 0xFFE8641E.toInt(), 0xFFF8B020.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        b.c.drawRect(CL, CT, CR, HORIZON + 2f, b.p)
        val touch = Random(7)
        repeat(160) {
            val x = CL + touch.nextFloat() * (CR - CL); val y = CT + touch.nextFloat() * (HORIZON - CT)
            val color = skyColor(y)
            val l = 4f + touch.nextFloat() * 7f
            b.line(x - l, y + touch.nextFloat() * 2f - 1f, x + l, y, 2.6f, withAlpha(lighten(color, touch.nextFloat() * .3f), 200))
        }
        b.glow(SUN_X, SUN_Y, 44f, 0xFFFFE070.toInt(), 200)
        b.c.drawCircle(SUN_X, SUN_Y, 17f, b.pen(CAD_YELLOW))
        repeat(28) {
            val a = touch.nextFloat() * 2f * PI.toFloat(); val r = touch.nextFloat() * 15f
            b.line(SUN_X + r * cos(a), SUN_Y + r * sin(a), SUN_X + r * cos(a) + 4f, SUN_Y + r * sin(a) + 1f, 2.4f, withAlpha(0xFFFFF0A0.toInt(), 170))
        }
        val painted = Path().apply {
            moveTo(CL, HORIZON); cubicTo(170f, 150f, 210f, 156f, 240f, 162f); cubicTo(260f, 166f, 270f, 158f, CR, 160f)
            lineTo(CR, CB); lineTo(CL, CB); close()
        }
        b.fill(painted, 0xFF5A2A6A.toInt())
        b.fill(rect(CL, 170f, CR, CB), 0xFF3A5A2A.toInt())
        for (row in 0 until 4) {
            val y = 174f + row * 6f
            val r = 2.6f + row * 1.2f
            var x = CL + 3f + row * 2f
            while (x < CR) {
                for (k in 0 until 6) {
                    val a = k * PI.toFloat() / 3f + touch.nextFloat() * .4f
                    b.line(x, y, x + r * cos(a), y + r * sin(a) * .8f, 2.2f, if (k % 2 == 0) CAD_YELLOW else CAD_ORANGE)
                }
                b.c.drawCircle(x, y, r * .45f, b.pen(0xFF4A2410.toInt()))
                x += r * 3f + touch.nextFloat() * 2f
            }
        }
        b.c.restore()
        b.stroke(canvas, 1.2f)
        b.fill(rect(CR, CT + 2f, CR + 4f, CB + 2f), 0xFFE8DCC0.toInt())
        b.stroke(rect(CR, CT + 2f, CR + 4f, CB + 2f), .7f)
        b.body(rect(CL - 8f, CB, CR + 10f, CB + 6f), 0xFF8A5A34.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(rect(EASEL_X - 4f, 74f, EASEL_X + 4f, CT + 6f), 0xFF8A5A34.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        // Le peintre, de dos : la blouse, le chapeau de paille, la palette dans la main gauche.
        val back = poly(86f, 236f, 166f, 236f, 186f, 262f, 194f, 340f, 56f, 340f, 64f, 262f)
        b.body(back, 0xFF4A6A9A.toInt(), .45f, 70f, Fade(60f, 0f, 190f, 0f, 60, 220), outline = 1f, washAlpha = 255)
        for (x in floatArrayOf(98f, 126f, 152f)) b.stroke(Path().apply { moveTo(x, 252f); quadTo(x + 4f, 296f, x - 2f, 338f) }, .7f, withAlpha(0xFF2A3A5A.toInt(), 180))
        b.body(rect(118f, 222f, 134f, 238f), 0xFFE0A884.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(ellipse(126f, 214f, 15f, 15f), 0xFF5A3A24.toInt(), .4f, 90f, outline = .9f, washAlpha = 255)
        b.body(ellipse(126f, 206f, 31f, 7.5f), 0xFFE8C878.toInt(), .3f, 0f, Fade(0f, 213f, 0f, 199f, 200, 20), outline = .9f, washAlpha = 255)
        b.body(Path().apply { addRoundRect(112f, 188f, 140f, 207f, 8f, 8f, Path.Direction.CW) }, 0xFFE8C878.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.fill(rect(112.5f, 200f, 139.5f, 204f), 0xFFA4281B.toInt())
        b.body(limb(88f, 244f, 70f, 292f, 15f, 12f), 0xFF4A6A9A.toInt(), .4f, 70f, outline = .9f, washAlpha = 255)
        val palette = ellipse(72f, 300f, 25f, 9f)
        b.body(palette, 0xFFC8A070.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        for ((i, color) in intArrayOf(CAD_YELLOW, CAD_ORANGE, CAD_RED, 0xFFFFFBF0.toInt()).withIndex()) {
            b.body(ellipse(56f + i * 10f, 298f + (i % 2) * 3f, 4f, 2.6f), color, 0f, outline = .5f, washAlpha = 255)
        }
        b.body(ellipse(70f, 293f, 6f, 5f), 0xFFE0A884.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
    }

    override fun sprites(): List<Sprite> = listOf(
        // Deux grands tournesols au premier plan, à droite ; ils se balancent sur leur tige.
        Sprite(FLOWER_A, RectF(268f, 212f, 320f, 334f)) { b -> sunflower(b, 300f, 330f, 294f, 238f, 13f) },
        Sprite(FLOWER_B, RectF(250f, 250f, 296f, 334f)) { b -> sunflower(b, 276f, 330f, 272f, 272f, 10f) }
    )

    private fun sunflower(b: Burin, x0: Float, y0: Float, x: Float, y: Float, r: Float) {
        b.stroke(Path().apply { moveTo(x0, y0); quadTo(x0 + 4f, (y0 + y) / 2f, x, y) }, 3f, 0xFF4A6A2A.toInt())
        b.body(ellipse(x0 + 9f, (y0 + y) / 2f + 10f, 9f, 4f), 0xFF5A8A3A.toInt(), .3f, 0f, outline = .6f, washAlpha = 255)
        for (k in 0 until 14) {
            val a = k * 2f * PI.toFloat() / 14f
            val px = x + (r + 3f) * cos(a); val py = y + (r + 3f) * sin(a) * .9f
            b.body(ellipse(px, py, 4.4f, 2.2f).apply {
                transform(android.graphics.Matrix().apply { setRotate(Math.toDegrees(a.toDouble()).toFloat(), px, py) })
            }, 0xFFE8B828.toInt(), 0f, outline = .5f, washAlpha = 255)
        }
        b.body(ellipse(x, y, r * .75f, r * .7f), 0xFF5A3418.toInt(), .5f, 0f, outline = .7f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Les tournesols dans le vent du soir.
        s.draw(c, FLOWER_A, deg = 2.2f * sin(t * 1.1f), px = 300f, py = 330f)
        s.draw(c, FLOWER_B, deg = 2.6f * sin(t * 1.3f + 1f), px = 276f, py = 330f)
        // Les touches fraîches des derniers passages : vives et brillantes, puis elles sèchent.
        val n = (t / TRIP).toInt()
        val u = t / TRIP - n
        for (j in 3 downTo 0) {
            val trip = n - j
            if (trip < 0) continue
            val since = t - (trip * TRIP + DAB_START)
            if (since <= 0f) continue
            val grow = (since / (DAB_END - DAB_START)).coerceAtMost(1f)
            val dry = ((since - (DAB_END - DAB_START)) / DRY).coerceIn(0f, 1f)
            if (dry >= 1f) continue
            val x = tipX(trip); val y = tipY(trip); val a = stroke(trip)
            val l = 7f * grow
            ink.color = withAlpha(dabColor(x, y), (255 * (1f - dry)).toInt()); ink.strokeWidth = 3.4f
            c.drawLine(x - 7f * cos(a), y - 7f * sin(a), x - 7f * cos(a) + 2f * l * cos(a), y - 7f * sin(a) + 2f * l * sin(a), ink)
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (150 * (1f - dry)).toInt()); ink.strokeWidth = 1f
            c.drawLine(x - 6f * cos(a), y - 6f * sin(a) - 1f, x - 6f * cos(a) + 1.6f * l * cos(a), y - 6f * sin(a) + 1.6f * l * sin(a) - 1f, ink)
        }
        // Le pinceau : il va d'une touche à la suivante, puis la pose d'un geste aller-retour.
        val m = smooth(u / MOVE)
        var x = tipX(n - 1) + (tipX(n) - tipX(n - 1)) * m
        var y = tipY(n - 1) + (tipY(n) - tipY(n - 1)) * m
        if (u > DAB_START / TRIP && u < DAB_END / TRIP) {
            val d = (u * TRIP - DAB_START) / (DAB_END - DAB_START)
            val a = stroke(n)
            val swing = 7f * sin(d * 3f * PI.toFloat())
            x += swing * cos(a); y += swing * sin(a)
        }
        // La main tient le pinceau un peu plus bas ; le bras suit (épaule, coude, poignet).
        val hx = x + HAND_DX; val hy = y + HAND_DY
        val dx = hx - SHOULDER_X; val dy = hy - SHOULDER_Y
        val d = hypot(dx, dy).coerceIn(ARM_1 - ARM_2 + 1f, ARM_1 + ARM_2 - .5f)
        val bend = acos(((ARM_1 * ARM_1 + d * d - ARM_2 * ARM_2) / (2f * ARM_1 * d)).coerceIn(-1f, 1f))
        val base = atan2(dy, dx)
        val ex = SHOULDER_X + ARM_1 * cos(base + bend); val ey = SHOULDER_Y + ARM_1 * sin(base + bend)
        ink.color = Ink.SEPIA; ink.strokeWidth = 17f
        c.drawLine(SHOULDER_X, SHOULDER_Y, ex, ey, ink)
        ink.strokeWidth = 14f; c.drawLine(ex, ey, hx, hy, ink)
        ink.color = SMOCK; ink.strokeWidth = 15f
        c.drawLine(SHOULDER_X, SHOULDER_Y, ex, ey, ink)
        ink.strokeWidth = 12f; c.drawLine(ex, ey, hx, hy, ink)
        ink.color = withAlpha(0xFF2A3A5A.toInt(), 160); ink.strokeWidth = .8f
        c.drawLine(ex - 3f, ey + 2f, ex + 4f, ey - 3f, ink)
        // Le manche, la virole, la pointe chargée de couleur ; la main par-dessus.
        ink.color = Ink.SEPIA; ink.strokeWidth = 3.4f; c.drawLine(hx - 6f, hy + 10f, x, y, ink)
        ink.color = 0xFF8A3A1A.toInt(); ink.strokeWidth = 2.2f; c.drawLine(hx - 6f, hy + 10f, x - 7f, y + 11f, ink)
        ink.color = 0xFFC8CCD0.toInt(); ink.strokeWidth = 2.6f; c.drawLine(x - 7f, y + 11f, x - 3f, y + 5f, ink)
        ink.color = dabColor(tipX(n), tipY(n)); ink.strokeWidth = 3f; c.drawLine(x - 3f, y + 5f, x, y, ink)
        fill.color = SKIN; c.drawCircle(hx, hy, 6.4f, fill)
        ink.color = Ink.SEPIA; ink.strokeWidth = .8f; c.drawCircle(hx, hy, 6.4f, ink)
    }

    /**
     * La touche du passage [n] : un point de la moitié gauche de la toile, à portée du bras, dans
     * le ciel ou dans le champ, jamais sur la ligne des collines.
     */
    private fun tipX(n: Int) = 148f + 70f * hash(n, 1)
    private fun tipY(n: Int): Float {
        val h = hash(n, 2)
        return if (h < .62f) 110f + 44f * h / .62f else 172f + 14f * (h - .62f) / .38f
    }
    private fun stroke(n: Int) = -.5f + hash(n, 3)

    /** La couleur de la touche selon l'endroit : rouge en haut du ciel, orange au milieu, jaune près du soleil et dans le champ. */
    private fun dabColor(x: Float, y: Float): Int = when {
        y > 168f -> CAD_YELLOW
        hypot(x - SUN_X, y - SUN_Y) < 18f -> CAD_YELLOW
        else -> skyColor(y)
    }

    private fun skyColor(y: Float): Int = when {
        y < CT + 22f -> CAD_RED
        y < CT + 46f -> CAD_ORANGE
        else -> 0xFFF8B020.toInt()
    }

    private fun lighten(color: Int, f: Float): Int {
        val r = ((color shr 16) and 255); val g = ((color shr 8) and 255); val bl = color and 255
        return (0xFF shl 24) or ((r + ((255 - r) * f).toInt()) shl 16) or ((g + ((255 - g) * f).toInt()) shl 8) or (bl + ((255 - bl) * f).toInt())
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** La toile, la ligne de ses collines peintes, son soleil. */
        const val CL = 134f
        const val CR = 278f
        const val CT = 92f
        const val CB = 194f
        const val HORIZON = 160f
        const val SUN_X = 168f
        const val SUN_Y = 134f
        const val EASEL_X = 207f
        const val CAD_YELLOW = 0xFFFFD21E.toInt()
        const val CAD_ORANGE = 0xFFF08A1E.toInt()
        const val CAD_RED = 0xFFD8321E.toInt()
        const val SMOCK = 0xFF4A6A9A.toInt()
        const val SKIN = 0xFFE0A884.toInt()
        /** Un passage du pinceau : le trajet, puis la touche entre [DAB_START] et [DAB_END] ; la touche sèche en [DRY]. */
        const val TRIP = 2.2f
        const val MOVE = .3f
        const val DAB_START = .8f
        const val DAB_END = 1.7f
        const val DRY = 4f
        /** Le bras droit : l'épaule, les longueurs du bras et de l'avant-bras ; la main sous la pointe. */
        const val SHOULDER_X = 168f
        const val SHOULDER_Y = 240f
        const val ARM_1 = 60f
        const val ARM_2 = 56f
        const val HAND_DX = -18f
        const val HAND_DY = 30f
        const val FLOWER_A = 1
        const val FLOWER_B = 2
    }
}
