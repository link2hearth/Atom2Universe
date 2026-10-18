package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin

/**
 * Mendélévium — le tableau périodique. L'élément porte le nom de Mendeleïev, qui a rangé les
 * éléments dans ce tableau. Sur le mur de la classe, une lumière parcourt le tableau case après
 * case, de l'hydrogène jusqu'au mendélévium ; sa case s'illumine d'or et la baguette du
 * professeur vient la montrer.
 */
internal class SceneMendelevium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_periodic_poster
    override val noteRes = R.string.card_note_periodic_poster
    override val explainRes = R.string.card_explain_periodic_poster

    override fun engrave(b: Burin) {
        // Le mur de la classe et son lambris.
        b.body(rect(40f, 50f, 320f, 340f), 0xFFC8D0B4.toInt(), .2f, 90f, outline = 0f, washAlpha = 255)
        b.body(rect(40f, 286f, 320f, 340f), 0xFF8A6040.toInt(), .35f, 0f, outline = .9f, washAlpha = 255)
        for (x in floatArrayOf(96f, 180f, 264f)) b.line(x, 294f, x, 340f, .8f, Ink.BROWN)
        // L'affiche : papier crème, bandeau de titre, quatre coins de ruban adhésif.
        b.body(rect(46f, 102f, 314f, 266f), 0xFFF4ECD8.toInt(), .1f, 0f, outline = .9f, washAlpha = 255)
        b.wash(rect(54f, 108f, 306f, 118f), Ink.RUBRIC, 220)
        b.goldStroke(rect(54f, 108f, 306f, 118f), .8f)
        for ((x, y) in listOf(48f to 104f, 312f to 104f, 312f to 264f, 48f to 264f)) {
            b.wash(poly(x - 8f, y - 3f, x + 3f, y - 8f, x + 8f, y + 3f, x - 3f, y + 8f), 0xFFF0E8B0.toInt(), 150)
        }
        // Les 118 cases, coloriées par famille ; un trait pour le symbole, un point pour le numéro.
        for (z in 1..118) {
            val x = CX[z]; val y = CY[z]
            b.wash(rect(x - HALF + .6f, y - HALF + .6f, x + HALF - .6f, y + HALF - .6f), family(z), 255)
            b.stroke(rect(x - HALF + .6f, y - HALF + .6f, x + HALF - .6f, y + HALF - .6f), .4f, Ink.BROWN)
            b.line(x - 3f, y + 1.5f, x + 3f, y + 1.5f, 1.6f, Ink.SEPIA)
            b.c.drawCircle(x - 4f, y - 4f, .8f, b.pen(Ink.SEPIA))
        }
        // Le renvoi des deux lignes du bas.
        b.line(CX[56] + 6f, CY[56] + 4f, CX[57] - 6f, CY[57] - 4f, .4f, Ink.BROWN)
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(CX[MD], CY[MD], 28f, intArrayOf(0xFFFFE89A.toInt(), 0x00FFE89A), null, Shader.TileMode.CLAMP)
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val tc = t % CYCLE
        // La lumière court de case en case ; elle laisse une traînée qui s'éteint derrière elle.
        val zf = MD * smooth01((tc - .3f) / 7f)
        val fade = 1f - smooth01((tc - 7.3f) / .8f)
        if (fade > 0f) {
            val first = (zf - TRAIL).toInt().coerceAtLeast(1)
            for (z in first..MD) {
                val head = (zf + 1f - z).coerceIn(0f, 1f)
                if (head <= 0f) break
                val glow = head * (1f - (zf - z) / TRAIL).coerceIn(0f, 1f) * fade
                fill.color = withAlpha(0xFFFFF4C0.toInt(), (220 * glow).toInt())
                c.drawRect(CX[z] - HALF, CY[z] - HALF, CX[z] + HALF, CY[z] + HALF, fill)
            }
        }
        // Arrivée au mendélévium : sa case s'illumine d'or et palpite.
        val md = smooth01((tc - 7f) / .5f) * (1f - smooth01((tc - 10f) / .6f))
        if (md > 0f) {
            val pulse = .5f + .5f * sin(t * 4f)
            halo.alpha = (200 * md * (.6f + .4f * pulse)).toInt()
            c.drawCircle(CX[MD], CY[MD], 28f, halo)
            fill.color = withAlpha(Gilding.LIGHT, (230 * md).toInt())
            c.drawRect(CX[MD] - HALF, CY[MD] - HALF, CX[MD] + HALF, CY[MD] + HALF, fill)
            line.color = withAlpha(Gilding.DEEP, (255 * md).toInt()); line.strokeWidth = 1.2f
            val grow = 1.5f + 2f * pulse
            c.drawRect(CX[MD] - HALF - grow, CY[MD] - HALF - grow, CX[MD] + HALF + grow, CY[MD] + HALF + grow, line)
            line.color = withAlpha(Ink.SEPIA, (255 * md).toInt()); line.strokeWidth = 1.6f
            c.drawLine(CX[MD] - 3f, CY[MD] + 1.5f, CX[MD] + 3f, CY[MD] + 1.5f, line)
        }
        // La baguette du professeur vient montrer la case, la tapote deux fois et repart.
        val come = smooth01((tc - 7.4f) / .7f) * (1f - smooth01((tc - 9.7f) / .7f))
        if (come > 0f) {
            val tap = if (tc in 8.3f..9.3f) sin((tc - 8.3f) * 6.2831855f).let { it * it } else 0f
            val pull = (1f - come) * 100f + 5f * tap
            val tipX = CX[MD] + 2f + DIR_X * pull; val tipY = CY[MD] + 2f + DIR_Y * pull
            val baseX = tipX + DIR_X * STICK; val baseY = tipY + DIR_Y * STICK
            line.color = 0xFF6A4020.toInt(); line.strokeWidth = 4.2f; c.drawLine(baseX, baseY, tipX, tipY, line)
            line.color = withAlpha(0xFFE0B888.toInt(), 200); line.strokeWidth = 1.2f
            c.drawLine(baseX - 1f, baseY - 1f, tipX - 1f, tipY - 1f, line)
            line.color = 0xFF1A1A1A.toInt(); line.strokeWidth = 4.6f
            c.drawLine(tipX + DIR_X * 6f, tipY + DIR_Y * 6f, tipX, tipY, line)
            // Chaque petit coup fait une onde dorée sur l'affiche.
            for (k in 0 until 2) {
                val age = tc - (8.8f + k * .5f)
                if (age < 0f || age > .6f) continue
                line.color = withAlpha(Gilding.LEAF, (200 * (1f - age / .6f)).toInt()); line.strokeWidth = 1f
                c.drawCircle(CX[MD], CY[MD], 6f + 30f * age, line)
            }
        }
    }

    private fun smooth01(x: Float): Float {
        val q = x.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    /** La couleur de famille d'un élément, comme sur les affiches de classe. */
    private fun family(z: Int): Int = when {
        z in 57..71 -> 0xFFF0B8D0.toInt()
        z in 89..103 -> 0xFFE0A8C8.toInt()
        z == 1 || z in intArrayOf(6, 7, 8, 15, 16, 34) -> 0xFFD0E8A0.toInt()
        z in intArrayOf(9, 17, 35, 53, 85, 117) -> 0xFFA8D8E8.toInt()
        z in intArrayOf(2, 10, 18, 36, 54, 86, 118) -> 0xFFC8B8E8.toInt()
        z in intArrayOf(3, 11, 19, 37, 55, 87) -> 0xFFE8A090.toInt()
        z in intArrayOf(4, 12, 20, 38, 56, 88) -> 0xFFF0C890.toInt()
        z in intArrayOf(5, 14, 32, 33, 51, 52) -> 0xFFB0D8C8.toInt()
        z in intArrayOf(13, 31, 49, 50, 81, 82, 83, 84, 113, 114, 115, 116) -> 0xFFC8D8B0.toInt()
        else -> 0xFFF0E0A8.toInt()
    }

    private companion object {
        const val CYCLE = 11f
        const val MD = 101
        const val TRAIL = 12f
        const val CELL = 14f
        const val HALF = 7f
        const val GX = 54f
        const val GY = 124f
        const val GY_F = 228f
        // La baguette arrive d'en bas à droite.
        const val DIR_X = .6f
        const val DIR_Y = .8f
        const val STICK = 150f
        // Le centre de chaque case, par numéro atomique ; les deux lignes du bas à part.
        val CX = FloatArray(119)
        val CY = FloatArray(119)

        init {
            for (z in 1..118) {
                val (col, row) = when {
                    z == 1 -> 0 to 0
                    z == 2 -> 17 to 0
                    z <= 4 -> z - 3 to 1
                    z <= 10 -> z + 7 to 1
                    z <= 12 -> z - 11 to 2
                    z <= 18 -> z - 1 to 2
                    z <= 36 -> z - 19 to 3
                    z <= 54 -> z - 37 to 4
                    z <= 56 -> z - 55 to 5
                    z <= 71 -> z - 55 to 7
                    z <= 86 -> z - 69 to 5
                    z <= 88 -> z - 87 to 6
                    z <= 103 -> z - 87 to 8
                    else -> z - 101 to 6
                }
                CX[z] = GX + col * CELL + HALF
                CY[z] = (if (row < 7) GY + row * CELL else GY_F + (row - 7) * CELL) + HALF
            }
        }
    }
}
