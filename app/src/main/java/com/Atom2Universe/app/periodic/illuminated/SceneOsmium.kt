package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Osmium — la plume du stylo. L'osmium, le plus dense des éléments, est aussi l'un des plus durs :
 * une bille de son alliage coiffe la pointe des plumes, qui glissent des années sans s'user. Le
 * stylo écrit une ligne de boucles à l'encre bleue, se relève, la feuille monte d'une ligne, et il
 * recommence. À la loupe, la bille d'osmium au bout de la plume.
 */
internal class SceneOsmium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_fountain_pen
    override val noteRes = R.string.card_note_fountain_pen
    override val explainRes = R.string.card_explain_fountain_pen

    override fun engrave(b: Burin) {
        // La feuille couvre toute la fenêtre ; les lignes réglées défilent dans l'animation.
        b.wash(rect(40f, 50f, 320f, 340f), 0xFFF6F0E0.toInt(), 255)
        b.stipple(rect(40f, 50f, 320f, 340f), 500, .45f, withAlpha(Ink.BROWN, 50), 8)
        b.line(64f, 50f, 64f, 340f, .8f, withAlpha(Ink.RUBRIC, 150))
    }

    override fun sprites() = listOf(
        // Le stylo, plume en bas à gauche, corps laqué noir vers le haut à droite.
        Sprite(PEN, RectF(TIP_X - 16f, TIP_Y - 160f, TIP_X + 150f, TIP_Y + 14f)) { b ->
            b.c.save(); b.c.rotate(-48f, TIP_X, TIP_Y)
            val nib = Path().apply {
                moveTo(TIP_X, TIP_Y); quadTo(TIP_X + 14f, TIP_Y - 9f, TIP_X + 38f, TIP_Y - 9f); lineTo(TIP_X + 38f, TIP_Y + 9f)
                quadTo(TIP_X + 14f, TIP_Y + 9f, TIP_X, TIP_Y); close()
            }
            b.gold(nib, Fade(TIP_X, TIP_Y - 9f, TIP_X, TIP_Y + 9f))
            b.stroke(nib, .8f)
            b.line(TIP_X + 1f, TIP_Y, TIP_X + 24f, TIP_Y, .7f)
            b.c.drawCircle(TIP_X + 24f, TIP_Y, 1.8f, b.pen(Ink.SEPIA))
            b.body(Path().apply { addRoundRect(TIP_X + 36f, TIP_Y - 11f, TIP_X + 56f, TIP_Y + 11f, 3f, 3f, Path.Direction.CW) }, 0xFF1A1A1E.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
            b.goldStroke(Path().apply { moveTo(TIP_X + 57f, TIP_Y - 12f); lineTo(TIP_X + 57f, TIP_Y + 12f) }, 2.4f)
            val barrel = Path().apply { addRoundRect(TIP_X + 58f, TIP_Y - 13f, TIP_X + 190f, TIP_Y + 13f, 12f, 12f, Path.Direction.CW) }
            b.body(barrel, 0xFF141418.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
            b.wash(rect(TIP_X + 62f, TIP_Y - 9f, TIP_X + 186f, TIP_Y - 5f), Ink.WHITE, 90)
            b.c.restore()
        },
        // La loupe : la pointe, sa fente, et la bille d'osmium.
        Sprite(LOUPE, RectF(LX - LR - 4f, LY - LR - 4f, LX + LR + 4f, LY + LR + 4f)) { b ->
            b.c.save(); b.c.clipPath(ellipse(LX, LY, LR, LR))
            b.wash(rect(LX - LR, LY - LR, LX + LR, LY + LR), 0xFFF6F0E0.toInt(), 255)
            val tine = Path().apply { moveTo(LX - 30f, LY - 40f); lineTo(LX + 4f, LY + 6f); lineTo(LX + 30f, LY - 40f); close() }
            b.gold(tine, Fade(LX - 30f, LY, LX + 30f, LY))
            b.stroke(tine, .9f)
            b.line(LX, LY - 40f, LX + 2f, LY + 2f, 1.2f)
            b.pen(0xFF000000.toInt()).shader = RadialGradient(LX - 3f, LY + 4f, 14f,
                intArrayOf(0xFFF4F6F8.toInt(), 0xFF8A9098.toInt(), 0xFF3A3E46.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            b.c.drawCircle(LX + 2f, LY + 8f, 10f, b.p)
            b.c.drawCircle(LX + 2f, LY + 8f, 10f, b.pen(Ink.SEPIA, .8f))
            b.c.drawPath(Path().apply { moveTo(LX - 30f, LY + 30f); quadTo(LX, LY + 16f, LX + 34f, LY + 26f) }, b.pen(0xFF1A2A6A.toInt(), 3f))
            b.c.restore()
            b.goldStroke(ellipse(LX, LY, LR + 1.5f, LR + 1.5f), 3f)
        }
    )

    // ───────────── animation ─────────────

    private val ruled = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .7f; color = withAlpha(0xFF5A8AC8.toInt(), 120) }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFF1A2A6A.toInt()
    }
    private val script = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val cycle = (t / CYCLE).toInt()
        val tc = t - cycle * CYCLE
        val p = (tc / WRITE).coerceAtMost(1f)
        val scroll = SPACING * smooth((tc - WRITE) / (CYCLE - WRITE))

        // Les lignes réglées montent avec la feuille.
        var y = BASE + 4f - scroll - SPACING * 6f
        while (y < 345f) { c.drawLine(40f, y, 320f, y, ruled); y += SPACING }
        // Les lignes déjà écrites, puis celle en cours.
        for (k in 5 downTo 1) writeLine(c, cycle - k, BASE - k * SPACING - scroll, 1f)
        writeLine(c, cycle, BASE - scroll, p)

        // Le stylo suit la pointe ; à la fin de la ligne, il se relève et revient au début.
        val px: Float; val py: Float
        if (tc < WRITE) {
            px = lineX(p, cycle); py = BASE + lineY(p, cycle)
        } else {
            val k = smooth((tc - WRITE) / (CYCLE - WRITE))
            px = lineX(1f, cycle) + (lineX(0f, cycle + 1) - lineX(1f, cycle)) * k
            py = BASE + lineY(1f, cycle) * (1f - k) + lineY(0f, cycle + 1) * k - 6f * sin(k * 3.1415927f)
        }
        s.draw(c, PEN, px - TIP_X, py - TIP_Y)
        s.draw(c, LOUPE)
    }

    private fun writeLine(c: Canvas, seed: Int, base: Float, upTo: Float) {
        if (base < 40f || upTo <= 0f) return
        script.reset()
        var down = false
        val n = (STEPS * upTo).toInt()
        for (i in 0..n) {
            val u = i / STEPS.toFloat()
            if (gap(u, seed)) { down = false; continue }
            val x = lineX(u, seed); val y = base + lineY(u, seed)
            if (!down) { script.moveTo(x, y); down = true } else script.lineTo(x, y)
        }
        c.drawPath(script, ink)
    }

    /** Une écriture en boucles : une cycloïde allongée, des hauteurs qui varient, des blancs entre les mots. */
    private fun lineX(u: Float, seed: Int) = 76f + 214f * u - 4.5f * sin(u * LOOPS * 6.2831855f)

    private fun lineY(u: Float, seed: Int): Float {
        val h = 5f + 3f * sin(u * 23f + seed * 1.7f)
        return -h * (1f - cos(u * LOOPS * 6.2831855f)) * .5f
    }

    private fun gap(u: Float, seed: Int) = sin(u * 31f + seed * 2.3f) > .93f

    private fun smooth(x: Float): Float {
        val q = x.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    private companion object {
        const val CYCLE = 6.2f
        const val WRITE = 5f
        const val SPACING = 28f
        const val BASE = 262f
        const val LOOPS = 26f
        const val STEPS = 260
        const val TIP_X = 120f
        const val TIP_Y = 250f
        const val LX = 98f
        const val LY = 104f
        const val LR = 36f
        const val PEN = 1
        const val LOUPE = 2
    }
}
