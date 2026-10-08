package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin

/**
 * La piscine d'un réacteur de recherche, commune aux éléments qu'on fabrique ou qu'on emploie
 * dans un réacteur. Vue plongeante : l'eau luit en bleu autour du cœur, les barres de contrôle
 * montent et descendent, une capsule descend dans le cœur, y reste, puis remonte en luisant.
 * Seule la légende change d'un élément à l'autre.
 */
internal class SceneReactorPool(
    override val noteRes: Int,
    override val nameRes: Int = R.string.card_scene_research_reactor,
    override val explainRes: Int = R.string.card_explain_research_reactor
) : EngravedScene() {

    override val sky = Sky.NONE

    override fun engrave(b: Burin) {
        // Les quais de béton autour de la piscine.
        val deck = rect(40f, 50f, 320f, 340f)
        b.body(deck, 0xFF8A8C88.toInt(), .3f, 20f, outline = 0f, washAlpha = 255)
        // Les parois qui plongent vers le fond, puis l'eau.
        val pool = poly(76f, 84f, 284f, 84f, 304f, 296f, 56f, 296f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 84f, 0f, 296f,
            0xFF1A5A80.toInt(), 0xFF0E3A5A.toInt(), Shader.TileMode.CLAMP)
        b.c.drawPath(pool, b.p)
        val walls = 0xFF2A7A9A.toInt()
        b.wash(poly(76f, 84f, 122f, 150f, 112f, 250f, 56f, 296f), walls, 120)
        b.wash(poly(284f, 84f, 238f, 150f, 248f, 250f, 304f, 296f), walls, 90)
        b.wash(poly(76f, 84f, 284f, 84f, 238f, 150f, 122f, 150f), 0xFF0A2A44.toInt(), 110)
        for ((x0, y0, x1, y1) in WALL_LINES) b.line(x0, y0, x1, y1, .8f, withAlpha(0xFF0A2030.toInt(), 160))
        // Le carrelage du fond.
        val bottom = poly(122f, 150f, 238f, 150f, 248f, 250f, 112f, 250f)
        b.wash(bottom, 0xFF3A8AAA.toInt(), 110)
        b.hatch(bottom, 0f, 10f, .5f, withAlpha(0xFF0A2030.toInt(), 120))
        b.hatch(bottom, 90f, 12f, .5f, withAlpha(0xFF0A2030.toInt(), 120))
        // Le cœur : vingt assemblages de combustible serrés en damier.
        for (j in 0 until 4) for (i in 0 until 5) {
            val x = 141f + i * 16f; val y = 172f + j * 15f
            b.body(rect(x, y, x + 14f, y + 13f), 0xFF5A7A8A.toInt(), .35f, 45f, outline = .6f, washAlpha = 255)
            b.wash(rect(x + 2f, y + 2f, x + 12f, y + 11f), 0xFFA8D0E0.toInt(), 90)
            for (k in 1..3) b.line(x + k * 3.5f, y + 2f, x + k * 3.5f, y + 11f, .4f, 0xFF2A4A5A.toInt())
        }
        // Le bord de la piscine et la rambarde du premier plan.
        b.stroke(pool, 1.4f)
        b.line(40f, 302f, 320f, 302f, 2.4f, 0xFFD8B030.toInt())
        for (x in floatArrayOf(60f, 120f, 180f, 240f, 300f)) b.line(x, 302f, x, 330f, 2.4f, 0xFFD8B030.toInt())
        b.line(40f, 318f, 320f, 318f, 1.6f, 0xFFB89020.toInt())
        // Le pont roulant au-dessus de l'eau, son chariot et les mécanismes des barres.
        b.body(rect(40f, 98f, 320f, 110f), 0xFF5A6A7A.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.wash(rect(40f, 98f, 320f, 101f), Ink.WHITE, 60)
        b.body(rect(CAP_X - 12f, 92f, CAP_X + 12f, 112f), 0xFFE0A030.toInt(), .25f, 90f, outline = .8f, washAlpha = 255)
        for (x in RODS) b.body(rect(x - 5f, 104f, x + 5f, 116f), 0xFF3A4A5A.toInt(), .3f, 90f, outline = .6f, washAlpha = 255)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cherenkov = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(180f, 202f, 120f, intArrayOf(0xF0A8E8FF.toInt(), 0x9040A8FF.toInt(), 0x302060E0, 0x002060E0),
            floatArrayOf(0f, .25f, .6f, 1f), Shader.TileMode.CLAMP)
    }
    private val caustic = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La lueur bleue de l'eau autour du cœur.
        cherenkov.alpha = (215 + 30 * sin(t * 1.3f) + 10 * sin(t * 4.1f)).toInt().coerceAtMost(255)
        c.drawCircle(180f, 202f, 120f, cherenkov)

        // Les reflets mouvants du fond.
        line.color = withAlpha(0xFFCFF4FF.toInt(), 30); line.strokeWidth = 1.2f
        for (k in 0 until 6) {
            caustic.reset()
            val y0 = 140f + k * 22f
            var x = 70f
            caustic.moveTo(x, y0 + 4f * sin(t * .9f + k))
            while (x < 290f) { x += 10f; caustic.lineTo(x, y0 + 4f * sin(x * .06f + t * .9f + k)) }
            c.drawPath(caustic, line)
        }

        // Les barres de contrôle, toutes ensemble, montent et descendent doucement.
        val depth = 196f + 16f * sin(t * TWO_PI / 14f)
        line.color = 0xFF2A4A60.toInt(); line.strokeWidth = 3.6f
        for (x in RODS) c.drawLine(x, 116f, x, depth, line)
        line.color = withAlpha(0xFFA8E8FF.toInt(), 120); line.strokeWidth = 1f
        for (x in RODS) c.drawLine(x - 1f, 118f, x - 1f, depth, line)

        // La capsule descend dans le cœur, y reste, remonte en luisant.
        val tc = t % CYCLE
        val p = when {
            tc < 3f -> ease(tc / 3f)
            tc < 6f -> 1f
            tc < 9f -> 1f - ease((tc - 6f) / 3f)
            else -> 0f
        }
        val y = 122f + (CAP_DEPTH - 122f) * p
        line.color = 0xFF1A2A3A.toInt(); line.strokeWidth = 1f
        c.drawLine(CAP_X, 112f, CAP_X, y, line)
        val hot = if (tc < 6f) smooth((tc - 3f) / 2f) else 1f - smooth((tc - 7f) / 3f)
        fill.color = 0xFFC8CCD2.toInt(); c.drawRoundRect(CAP_X - 4f, y, CAP_X + 4f, y + 14f, 3f, 3f, fill)
        if (hot > 0f) {
            fill.color = withAlpha(0xFF9AE0FF.toInt(), (110 * hot).toInt()); c.drawCircle(CAP_X, y + 7f, 12f, fill)
            fill.color = withAlpha(0xFFE8FAFF.toInt(), (200 * hot).toInt()); c.drawRoundRect(CAP_X - 3f, y + 2f, CAP_X + 3f, y + 12f, 2f, 2f, fill)
        }

        // Quelques bulles montent du cœur.
        for (k in 0 until 8) {
            val period = 2.2f + hash(k, 1)
            val off = period * hash(k, 2)
            val n = ((t + off) / period).toInt()
            val age = ((t + off) - n * period) / period
            val x = 146f + 70f * hash(k, n) + 3f * sin(age * 8f + k)
            val by = 186f - 40f * age
            fill.color = withAlpha(0xFFE0F6FF.toInt(), (150 * sin(age * PI)).toInt())
            c.drawCircle(x, by, .8f + 2f * age, fill)
        }
    }

    private fun ease(x: Float) = x * x * (3f - 2f * x)

    private fun smooth(x: Float): Float = ease(x.coerceIn(0f, 1f))

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CYCLE = 10f
        const val CAP_X = 196f
        const val CAP_DEPTH = 180f
        const val TWO_PI = 6.2831855f
        const val PI = 3.1415927f
        val RODS = floatArrayOf(150f, 166f, 214f, 230f)
        val WALL_LINES = listOf(
            floatArrayOf(76f, 84f, 122f, 150f), floatArrayOf(284f, 84f, 238f, 150f),
            floatArrayOf(56f, 296f, 112f, 250f), floatArrayOf(304f, 296f, 248f, 250f)
        )
    }
}
