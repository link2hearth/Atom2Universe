package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plomb — le plomb de pêche. Lourd, mou, facile à fondre : le plomb a longtemps lesté les lignes.
 * Le bouchon tombe sur l'eau ; le plomb l'entraîne vers le fond avec l'hameçon et son ver, dans
 * un filet de bulles. Un poisson approche, mordille, le bouchon plonge ; la ligne est ferrée et
 * remonte d'un coup, le poisson file.
 */
internal class SceneLead : EngravedScene() {

    override val sky = Sky.DAY
    override val nameRes = R.string.card_scene_fishing_sinker
    override val noteRes = R.string.card_note_fishing_sinker
    override val explainRes = R.string.card_explain_fishing_sinker

    override fun engrave(b: Burin) {
        // La rive au loin.
        b.body(Path().apply {
            moveTo(40f, 92f); quadTo(90f, 74f, 140f, 86f); quadTo(200f, 70f, 260f, 84f); quadTo(296f, 76f, 320f, 88f)
            lineTo(320f, SURF); lineTo(40f, SURF); close()
        }, 0xFF5A7A4A.toInt(), .45f, 70f, outline = .7f, washAlpha = 255)
        // L'eau, verte en haut, sombre au fond, et le fond de gravier.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, SURF, 0f, 300f,
            0xFF4A9A9A.toInt(), 0xFF0E2A30.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, SURF, 320f, 340f, b.p)
        b.hatchRect(RectF(40f, SURF, 320f, 300f), 0f, 4.2f, .4f, withAlpha(0xFF000000.toInt(), 60))
        b.line(40f, SURF, 320f, SURF, 1.2f, withAlpha(Ink.WHITE, 200))
        val bed = Path().apply { moveTo(40f, 286f); quadTo(140f, 276f, 220f, 288f); quadTo(280f, 294f, 320f, 282f); lineTo(320f, 340f); lineTo(40f, 340f); close() }
        b.body(bed, 0xFF6A5A40.toInt(), .45f, 20f, outline = .8f, washAlpha = 255)
        val rnd = kotlin.random.Random(4)
        repeat(40) {
            val x = 44f + rnd.nextFloat() * 272f; val y = 290f + rnd.nextFloat() * 40f
            b.body(ellipse(x, y, 2f + rnd.nextFloat() * 4f, 1.5f + rnd.nextFloat() * 2f), 0xFF8A8070.toInt(), .3f, 30f, outline = .4f, washAlpha = 255)
        }
    }

    override fun sprites() = listOf(
        // Le bouchon rouge et blanc.
        Sprite(FLOAT, RectF(RIG_X - 8f, REF - 16f, RIG_X + 8f, REF + 16f)) { b ->
            b.body(ellipse(RIG_X, REF - 4f, 6f, 8f), 0xFFD8302A.toInt(), .2f, 40f, outline = .7f, washAlpha = 255)
            b.body(ellipse(RIG_X, REF + 5f, 6f, 7f), 0xFFF4F2EA.toInt(), .15f, 40f, outline = .7f, washAlpha = 255)
            b.line(RIG_X, REF - 15f, RIG_X, REF - 11f, 1.4f, 0xFFD8302A.toInt())
        },
        // L'olive de plomb, gris mat.
        Sprite(SINKER, RectF(RIG_X - 8f, REF - 10f, RIG_X + 8f, REF + 10f)) { b ->
            b.pen(0xFF000000.toInt()).shader = RadialGradient(RIG_X - 2f, REF - 3f, 9f,
                intArrayOf(0xFFB8BCC4.toInt(), 0xFF6A6E78.toInt(), 0xFF3A3E46.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            b.c.drawOval(RIG_X - 5f, REF - 8f, RIG_X + 5f, REF + 8f, b.p)
            b.c.drawOval(RIG_X - 5f, REF - 8f, RIG_X + 5f, REF + 8f, b.pen(Ink.SEPIA, .7f))
        },
        // Une perche, rayée de sombre.
        Sprite(FISH, RectF(FISH_X - 30f, REF - 14f, FISH_X + 30f, REF + 14f)) { b ->
            val body = Path().apply {
                moveTo(FISH_X - 24f, REF); quadTo(FISH_X - 6f, REF - 14f, FISH_X + 16f, REF - 3f); lineTo(FISH_X + 28f, REF - 10f)
                lineTo(FISH_X + 26f, REF); lineTo(FISH_X + 28f, REF + 10f); lineTo(FISH_X + 16f, REF + 3f); quadTo(FISH_X - 6f, REF + 12f, FISH_X - 24f, REF); close()
            }
            b.body(body, 0xFF8AA04A.toInt(), .25f, 80f, outline = .8f, washAlpha = 255)
            for (k in 0 until 4) b.line(FISH_X - 10f + k * 7f, REF - 9f, FISH_X - 12f + k * 7f, REF + 6f, 2f, withAlpha(0xFF2A3A1A.toInt(), 150))
            b.c.drawCircle(FISH_X - 17f, REF - 2f, 1.8f, b.pen(Ink.SEPIA))
            b.body(poly(FISH_X - 4f, REF - 10f, FISH_X + 8f, REF - 16f, FISH_X + 10f, REF - 6f), 0xFFC8582A.toInt(), 0f, outline = .5f, washAlpha = 230)
        }
    )

    // ───────────── animation ─────────────

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val worm = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val tc = t % CYCLE
        val sway = 2f * sin(t * .8f)
        val x = RIG_X + sway
        // Le bouchon : il tombe, flotte, plonge quand le poisson mordille, puis la ligne le soulève.
        val dips = 5f * exp(-sq((tc - 5.5f) / .12f)) + 7f * exp(-sq((tc - 6.3f) / .12f))
        val floatY = when {
            tc < .6f -> -20f + (SURF + 20f) * sq(tc / .6f)
            tc < 7f -> SURF + 1.5f * sin(t * 2f) + dips
            tc < 8f -> SURF - (SURF + 20f) * sq(tc - 7f)
            else -> -20f
        }
        // Le plomb : il pend sous le bouchon, coule, se pose, puis remonte d'un coup.
        val sinkY = when {
            tc < .6f -> floatY + 22f
            tc < 3.4f -> SURF + 22f + (BED - SURF - 22f) * easeOut((tc - .6f) / 2.8f)
            tc < 7f -> BED
            tc < 8f -> BED - (BED + 20f) * sq(tc - 7f)
            else -> -20f
        }
        line.color = withAlpha(Ink.WHITE, 170); line.strokeWidth = .8f
        c.drawLine(x - 40f, 40f, x, floatY - 14f, line)
        line.color = withAlpha(0xFFE8F0F0.toInt(), 130)
        c.drawLine(x, floatY + 10f, x, sinkY - 8f, line)
        c.drawLine(x, sinkY + 8f, x + 2f, sinkY + 22f, line)
        // L'hameçon et son ver qui se tortille.
        line.color = 0xFF9AA0A8.toInt(); line.strokeWidth = 1.2f
        c.drawArc(x - 2f, sinkY + 22f, x + 6f, sinkY + 32f, 0f, 200f, false, line)
        worm.reset()
        worm.moveTo(x + 4f, sinkY + 25f)
        worm.quadTo(x + 12f + 4f * sin(t * 5f), sinkY + 30f, x + 8f, sinkY + 38f + 2f * sin(t * 4f))
        line.color = 0xFFC86A6A.toInt(); line.strokeWidth = 3f
        c.drawPath(worm, line)
        s.draw(c, SINKER, sway, sinkY - REF)
        s.draw(c, FLOAT, sway, floatY - REF)

        // Les bulles qui suivent le plomb dans sa descente.
        for (k in 0 until 10) {
            val born = .6f + k * .26f
            val age = tc - born
            if (age < 0f || age > 2f) continue
            val y0 = SURF + 22f + (BED - SURF - 22f) * easeOut((born - .6f) / 2.8f)
            val y = y0 - 40f * age
            if (y < SURF + 3f) continue
            fill.color = withAlpha(Ink.WHITE, (150 * (1f - age / 2f)).toInt())
            c.drawCircle(x + 3f * sin(age * 6f + k), y, 1.4f + .4f * (k % 3), fill)
        }
        // Les ronds dans l'eau, à l'arrivée du bouchon et quand on ferre.
        ripples(c, x, tc - .6f)
        ripples(c, x, tc - 7f)
        // Le poisson arrive, mordille, puis file quand on ferre.
        val fx = when {
            tc < 3.4f -> 360f
            tc < 5f -> 360f - (360f - 236f) * easeOut((tc - 3.4f) / 1.6f)
            tc < 7f -> 236f + 6f * sin((tc - 5f) * 5f)
            tc < 8.2f -> 236f - 300f * sq((tc - 7f) / 1.2f)
            else -> -70f
        }
        val fy = BED + 30f + 3f * sin(t * 1.3f)
        s.draw(c, FISH, fx - FISH_X, fy - REF)
    }

    private fun ripples(c: Canvas, x: Float, age: Float) {
        if (age < 0f || age > 1.6f) return
        line.strokeWidth = 1f
        for (k in 0 until 2) {
            val a = age - k * .3f
            if (a < 0f) continue
            line.color = withAlpha(Ink.WHITE, (180 * (1f - a / 1.6f)).toInt().coerceAtLeast(0))
            c.drawOval(x - 6f - 40f * a, SURF - 2f - 3f * a, x + 6f + 40f * a, SURF + 2f + 3f * a, line)
        }
    }

    private fun sq(x: Float) = x * x

    private fun easeOut(x: Float): Float {
        val q = x.coerceIn(0f, 1f)
        return 1f - (1f - q) * (1f - q)
    }

    private companion object {
        const val CYCLE = 10f
        const val SURF = 100f
        const val BED = 248f
        const val RIG_X = 196f
        const val REF = 150f
        const val FISH_X = 240f
        const val FLOAT = 1
        const val SINKER = 2
        const val FISH = 3
    }
}
