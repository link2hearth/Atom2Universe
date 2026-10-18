package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cérium — la pierre à briquet. Le pouce roule la molette d'acier, qui râpe la pierre au cérium :
 * les copeaux prennent feu seuls dans l'air, la gerbe d'étincelles part vers le gaz, la flamme
 * s'allume tant que le pouce tient le bouton. Dans le corps transparent, le gaz liquide bouillonne.
 */
internal class SceneCerium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_lighter_flint
    override val noteRes = R.string.card_note_lighter_flint
    override val explainRes = R.string.card_explain_lighter_flint

    override fun engrave(b: Burin) {
        // Une pièce dans la pénombre.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            0xFF1C140E.toInt(), 0xFF0A0706.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 340f, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 60f, 3.4f, .4f, withAlpha(0xFF000000.toInt(), 120))
        // Le corps en plastique transparent, le gaz liquide dedans.
        val body = Path().apply { addRoundRect(132f, 176f, 218f, 352f, 16f, 16f, Path.Direction.CW) }
        b.wash(body, 0xFF2F7FB0.toInt(), 150)
        b.c.save(); b.c.clipPath(body)
        b.wash(rect(130f, LIQUID, 220f, 352f), 0xFF1E5C88.toInt(), 150)
        b.line(132f, LIQUID, 218f, LIQUID, 1.2f, withAlpha(0xFFA8D8F0.toInt(), 200))
        b.wash(rect(140f, 180f, 147f, 352f), Ink.WHITE, 70)
        b.c.restore()
        b.tone(body, .35f, 80f, fade = Fade(218f, 0f, 170f, 0f, 220, 0))
        b.stroke(body, 1f)
        // La tige du fond, qui porte la pierre et son ressort.
        b.line(204f, 182f, 204f, 340f, 2.4f, withAlpha(0xFF0E2A40.toInt(), 160))
        // Le bouchon noir.
        b.body(Path().apply { addRoundRect(130f, 166f, 220f, 180f, 3f, 3f, Path.Direction.CW) }, 0xFF1E1E22.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        // Le capot de métal, percé de deux rangées de trous.
        val hood = Path().apply { addRoundRect(136f, 126f, 200f, 168f, 4f, 4f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(136f, 0f, 200f, 0f,
            intArrayOf(0xFF8A8E94.toInt(), 0xFFE8EAEE.toInt(), 0xFFA0A4AA.toInt(), 0xFF5A5E64.toInt()), floatArrayOf(0f, .3f, .65f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(hood, b.p)
        b.stroke(hood, .9f)
        for (row in 0 until 2) for (i in 0 until 5) b.wash(ellipse(146f + i * 11f, 144f + row * 11f, 3f, 3.6f), 0xFF0A0A0C.toInt(), 230)
        // Les flasques qui tiennent la molette.
        b.body(poly(198f, 120f, 222f, 120f, 222f, 168f, 198f, 168f), 0xFF7A7E84.toInt(), .4f, 90f, outline = .8f, washAlpha = 255)
        // La buse, dans l'ouverture du capot.
        b.body(rect(FX - 4f, FY, FX + 4f, FY + 6f), 0xFFB08A3A.toInt(), .3f, 90f, outline = .6f, washAlpha = 255)
    }

    override fun sprites() = listOf(
        Sprite(WHEEL, RectF(WX - 14f, WY - 14f, WX + 14f, WY + 14f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(WX - 4f, WY - 4f, 16f,
                intArrayOf(0xFFE4E6EA.toInt(), 0xFF8A8E94.toInt(), 0xFF3A3C40.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(WX, WY, 12f, b.p)
            for (k in 0 until 30) {
                val a = k * 12f * DEG
                b.line(WX + 9f * cos(a), WY + 9f * sin(a), WX + 12f * cos(a), WY + 12f * sin(a), .9f, 0xFF2A2C30.toInt())
            }
            b.c.drawCircle(WX, WY, 12f, b.pen(Ink.SEPIA, .8f))
            b.c.drawCircle(WX, WY, 3f, b.pen(0xFF4A4E54.toInt()))
            b.c.drawCircle(WX, WY, 3f, b.pen(Ink.SEPIA, .6f))
        },
        Sprite(BUTTON, RectF(200f, 140f, 244f, 168f)) { b ->
            b.body(poly(206f, 152f, 236f, 148f, 239f, 158f, 208f, 163f), 0xFFC8281E.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.line(208f, 153f, 234f, 149.5f, .8f, withAlpha(Ink.WHITE, 150))
        },
        Sprite(THUMB, RectF(196f, 70f, 336f, 176f)) { b ->
            // Le pouce, couché vers la gauche, la pulpe sur le bouton.
            val thumb = limb(222f, 141f, 330f, 104f, 24f, 32f)
            b.body(thumb, 0xFFE2A882.toInt(), 0f, outline = 0f, washAlpha = 255)
            b.washGradient(thumb, 0xFF8A4A30.toInt(), 0f, 112f, 0, 0f, 156f, 150)
            b.tone(thumb, .3f, 20f, fade = Fade(0f, 156f, 0f, 120f, 200, 0))
            b.stroke(thumb, 1f)
            // L'ongle, sur le dessus près du bout.
            val nail = ellipse(0f, 0f, 11f, 6f)
            nail.transform(Matrix().apply { setRotate(-20f); postTranslate(232f, 128f) })
            b.body(nail, 0xFFF2CDB8.toInt(), 0f, outline = .8f, washAlpha = 255)
            b.wash(ellipse(229f, 127f, 5f, 2f), Ink.WHITE, 120)
            // Les plis de l'articulation.
            b.line(262f, 112f, 268f, 134f, .8f, Ink.BROWN)
            b.line(266f, 110f, 272f, 131f, .6f, Ink.BROWN)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(FX, FY - 30f, 150f, intArrayOf(0x66FFB050, 0x22FF9030, 0x00FF9030), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    }
    private val flame = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val cycle = (t / CYCLE).toInt()
        val tc = t - cycle * CYCLE

        // La molette ne tourne que pendant le coup de pouce.
        val strike = ease((tc / STRIKE).coerceIn(0f, 1f))
        s.draw(c, WHEEL, deg = (cycle + strike) * 140f, px = WX, py = WY)
        val press = smooth((tc - .22f) / .1f) * (1f - smooth((tc - RELEASE) / .12f))
        s.draw(c, BUTTON, dy = 2f * press)

        // Le pouce : posé sur la molette, il la roule jusqu'au bouton, tient, se relève, revient.
        var dx: Float; var dy: Float
        when {
            tc < STRIKE -> { dx = -17f + 17f * strike; dy = -35f + 35f * strike - 9f * sin(strike * PI) }
            tc < RELEASE -> { dx = 0f; dy = 0f }
            tc < RELEASE + .25f -> { val k = ease((tc - RELEASE) / .25f); dx = 2f * k; dy = -8f * k }
            tc < AWAY -> { val k = ease((tc - RELEASE - .25f) / (AWAY - RELEASE - .25f)); dx = 2f - 8f * k; dy = -8f - 54f * k }
            else -> { val k = ease((tc - AWAY) / (CYCLE - AWAY)); dx = -6f - 11f * k; dy = -62f + 27f * k }
        }
        s.draw(c, THUMB, dx, dy + 2f * press)

        // La flamme et la lumière qu'elle jette.
        val on = smooth((tc - .22f) / .18f) * (1f - smooth((tc - RELEASE) / .18f))
        if (on > 0f) {
            val flicker = 1f + .08f * sin(t * 13f) + .05f * sin(t * 21f + 1f)
            halo.alpha = (255 * on * (.85f + .15f * sin(t * 17f))).toInt()
            c.drawCircle(FX, FY - 30f, 150f, halo)
            val h = 54f * on * flicker
            val w = 9f * on * (1f + .1f * sin(t * 9f))
            val sway = 2.5f * sin(t * 5.3f)
            drop(h, w, sway)
            fill.color = withAlpha(0xFFFFA838.toInt(), (215 * on).toInt()); c.drawPath(flame, fill)
            fill.color = withAlpha(0xFF4A7AFF.toInt(), (150 * on).toInt())
            c.drawOval(FX - w * .9f, FY - h * .2f, FX + w * .9f, FY + 1f, fill)
            drop(h * .55f, w * .55f, sway * .6f)
            fill.color = withAlpha(0xFFFFF4C8.toInt(), (235 * on).toInt()); c.drawPath(flame, fill)
        }

        // Le gaz liquide bouillonne tant que la valve est ouverte.
        streak.color = withAlpha(0xFFCDEBFA.toInt(), 170); streak.strokeWidth = .7f
        for (k in 0 until 7) {
            val period = 1.1f + .3f * hash(k, 1)
            val off = period * hash(k, 2)
            val n = ((t + off) / period).toInt()
            val birth = n * period - off
            val phase = ((birth % CYCLE) + CYCLE) % CYCLE
            if (phase < .3f || phase > RELEASE) continue
            val age = (t - birth) / .9f
            if (age >= 1f) continue
            val x = 150f + 50f * hash(k, n) + 2f * sin(age * 9f + k)
            val y = 318f - (318f - LIQUID - 3f) * age
            c.drawCircle(x, y, 1.2f + 1.3f * hash(n, k), streak)
        }

        // La gerbe d'étincelles, jetée par la molette vers le gaz.
        if (tc < 1f) for (k in 0 until SPARKS) {
            val seed = k + cycle * 31
            val birth = .05f + .25f * hash(seed, 1)
            val life = .18f + .35f * hash(seed, 2)
            val age = tc - birth
            if (age < 0f || age > life) continue
            val a = (-12f + 52f * hash(seed, 3)) * DEG
            val v = 90f + 170f * hash(seed, 4)
            val vx = -cos(a) * v; val vy = -sin(a) * v
            val x = SPARK_X + vx * age; val y = SPARK_Y + vy * age + .5f * GRAVITY * age * age
            val k2 = age / life
            streak.color = withAlpha(if (k2 < .35f) 0xFFFFF6D0.toInt() else if (k2 < .7f) 0xFFFFC040.toInt() else 0xFFFF7A20.toInt(), (255 * (1f - k2)).toInt())
            streak.strokeWidth = 1.4f
            c.drawLine(x, y, x - vx * .025f, y - (vy + GRAVITY * age) * .025f, streak)
        }
    }

    /** Une goutte de flamme posée sur la buse. */
    private fun drop(h: Float, w: Float, sway: Float) {
        flame.reset()
        flame.moveTo(FX - w, FY)
        flame.cubicTo(FX - w * 1.4f, FY - h * .45f, FX + sway - w * .4f, FY - h * .8f, FX + sway, FY - h)
        flame.cubicTo(FX + sway + w * .4f, FY - h * .8f, FX + w * 1.4f, FY - h * .45f, FX + w, FY)
        flame.quadTo(FX, FY + w * .6f, FX - w, FY)
        flame.close()
    }

    private fun ease(x: Float) = x * x * (3f - 2f * x)

    private fun smooth(x: Float): Float = ease(x.coerceIn(0f, 1f))

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CYCLE = 5f
        const val STRIKE = .35f
        const val RELEASE = 3.4f
        const val AWAY = 4.3f
        const val LIQUID = 214f
        const val WX = 210f
        const val WY = 128f
        const val FX = 164f
        const val FY = 122f
        const val SPARK_X = 200f
        const val SPARK_Y = 136f
        const val GRAVITY = 260f
        const val SPARKS = 26
        const val WHEEL = 1
        const val BUTTON = 2
        const val THUMB = 3
        const val PI = 3.1415927f
        const val DEG = .017453292f
    }
}
