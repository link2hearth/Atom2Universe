package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ruthénium — le disque dur. Une couche de ruthénium de trois atomes d'épaisseur, glissée dans
 * les plateaux des disques durs, a permis d'y loger bien plus de données. Le disque est ouvert
 * sur l'établi : le plateau miroir tourne, et le bras de lecture saute de piste en piste avec un
 * petit rebond à chaque arrêt.
 */
internal class SceneRuthenium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_hard_drive
    override val noteRes = R.string.card_note_hard_drive
    override val explainRes = R.string.card_explain_hard_drive

    override fun engrave(b: Burin) {
        // Le tapis antistatique, un tournevis.
        val mat = rect(40f, 50f, 320f, 340f)
        b.wash(mat, 0xFF2A4A5A.toInt(), 255)
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 45f, 3f, .35f, 0xFF1A3440.toInt())
        b.body(limb(292f, 70f, 304f, 150f, 9f, 9f), 0xFFC83A2A.toInt(), .35f, 0f, outline = .9f, washAlpha = 255)
        b.body(limb(304f, 150f, 308f, 190f, 3f, 2f), 0xFF9AA2AA.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        // Le boîtier d'aluminium, son joint, ses vis.
        val case = Path().apply { addRoundRect(CASE_L, CASE_T, CASE_R, CASE_B, 8f, 8f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(CASE_L, CASE_T, CASE_R, CASE_B,
            intArrayOf(0xFFD8DCE0.toInt(), 0xFFA8B0B8.toInt(), 0xFFC8CED4.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(case, b.p)
        b.stroke(case, 1.2f)
        val well = Path().apply {
            addCircle(PX, PY, PLATTER + 6f, Path.Direction.CW)
            op(Path().apply { addRoundRect(196f, 196f, 282f, 302f, 14f, 14f, Path.Direction.CW) }, Path.Op.UNION)
        }
        b.body(well, 0xFF7A828C.toInt(), .3f, 0f, outline = 1f, washAlpha = 255)
        for ((x, y) in arrayOf(CASE_L + 8f to CASE_T + 8f, CASE_R - 8f to CASE_T + 8f, CASE_L + 8f to CASE_B - 8f, CASE_R - 8f to CASE_B - 8f, CASE_L + 8f to 190f, CASE_R - 8f to 190f)) {
            b.body(ellipse(x, y, 3.4f, 3.4f), 0xFF8A9096.toInt(), .2f, 0f, outline = .7f, washAlpha = 255)
            b.c.drawCircle(x, y, 1.2f, b.pen(0xFF3A3E44.toInt()))
        }
        // Le plateau : un miroir qui reflète la pièce, ses pistes.
        b.pen(0xFF000000.toInt()).shader = SweepGradient(PX, PY,
            intArrayOf(0xFFE8ECF0.toInt(), 0xFF7A828C.toInt(), 0xFFF4F6F8.toInt(), 0xFF5A626C.toInt(), 0xFFE8ECF0.toInt(), 0xFF8A929C.toInt(), 0xFFE8ECF0.toInt()), null)
        b.c.drawCircle(PX, PY, PLATTER, b.p)
        var r = 26f
        while (r < PLATTER) { b.c.drawCircle(PX, PY, r, b.pen(withAlpha(0xFF4A525C.toInt(), 40), .5f)); r += 4f }
        b.c.drawCircle(PX, PY, PLATTER, b.pen(Ink.SEPIA, 1f))
        // L'aimant de la bobine du bras, en bas à droite ; la nappe.
        b.body(Path().apply { addRoundRect(226f, 250f, 280f, 296f, 6f, 6f, Path.Direction.CW) }, 0xFF5A6068.toInt(), .45f, 0f, outline = .9f, washAlpha = 255)
        b.body(rect(196f, 292f, 238f, 300f), 0xFFC8902A.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
    }

    override fun sprites(): List<Sprite> = listOf(
        // Le moyeu et ses six vis : il tourne avec le plateau.
        Sprite(HUB, RectF(-18f, -18f, 18f, 18f)) { b ->
            b.body(ellipse(0f, 0f, 17f, 17f), 0xFFC8CED4.toInt(), .2f, 0f, outline = 1f, washAlpha = 255)
            b.c.drawCircle(0f, 0f, 12f, b.pen(withAlpha(0xFF5A626C.toInt(), 160), .7f))
            for (k in 0 until 6) {
                val a = k * PI.toFloat() / 3f
                val x = 9f * cos(a); val y = 9f * sin(a)
                b.body(ellipse(x, y, 2.6f, 2.6f), 0xFF8A9096.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
                b.line(x - 1.4f, y, x + 1.4f, y, .6f, 0xFF2A2C30.toInt())
            }
            b.c.drawCircle(0f, 0f, 3f, b.pen(0xFF5A626C.toInt()))
        },
        // Le bras de lecture, pivot en (0, 0), pointé vers la gauche ; la tête au bout.
        Sprite(ARM, RectF(-ARM_L - 8f, -16f, 22f, 16f)) { b ->
            val arm = Path().apply {
                moveTo(10f, -12f); lineTo(-ARM_L * .5f, -6f); lineTo(-ARM_L, -2f); lineTo(-ARM_L, 2f)
                lineTo(-ARM_L * .5f, 6f); lineTo(10f, 12f); close()
            }
            b.body(arm, 0xFFC8CED4.toInt(), .3f, 0f, Fade(0f, 12f, 0f, -12f, 200, 30), outline = .9f, washAlpha = 255)
            b.fill(poly(-ARM_L * .4f, -3f, -ARM_L * .7f, -1.6f, -ARM_L * .7f, 1.6f, -ARM_L * .4f, 3f), 0xFF7A828C.toInt())
            b.body(rect(-ARM_L - 6f, -3f, -ARM_L + 2f, 3f), 0xFF3A3E44.toInt(), .2f, 0f, outline = .6f, washAlpha = 255)
            b.line(-ARM_L * .3f, 4f, 4f, 9f, 1f, 0xFFC8902A.toInt())
            b.body(ellipse(0f, 0f, 11f, 11f), 0xFF9AA2AA.toInt(), .3f, 0f, outline = 1f, washAlpha = 255)
            b.c.drawCircle(0f, 0f, 3.4f, b.pen(0xFF3A3E44.toInt()))
            // La bobine, derrière le pivot.
            b.body(poly(10f, -12f, 22f, -14f, 22f, 14f, 10f, 12f), 0xFFB8742A.toInt(), .35f, 0f, outline = .8f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // Le plateau tourne : le moyeu et un léger reflet qui court sur les pistes.
        s.draw(c, HUB, dx = PX, dy = PY, deg = t * 160f)
        val glint = t * 2.8f
        fill.color = withAlpha(0xFFFFFFFF.toInt(), 46)
        for (k in 0 until 2) {
            val a = glint + k * PI.toFloat()
            val r = 52f
            c.drawCircle(PX + r * cos(a), PY + r * sin(a), 10f, fill)
        }
        // Le bras cherche une piste : bascule rapide, petit rebond qui s'amortit, attente.
        val step = (t / SEEK).toInt()
        val since = t - step * SEEK
        val from = angle(step - 1); val to = angle(step)
        val move = (since / .14f).coerceAtMost(1f)
        val settle = if (since > .14f) 1.4f * exp(-(since - .14f) * 18f) * sin((since - .14f) * 60f) else 0f
        val deg = from + (to - from) * move * move * (3f - 2f * move) + settle
        s.draw(c, ARM, dx = AX, dy = AY, deg = deg)
        // Le témoin d'activité qui clignote à chaque déplacement.
        fill.color = if (since < .2f) 0xFF5AE87A.toInt() else 0xFF2A5A3A.toInt()
        c.drawCircle(CASE_L + 14f, CASE_B - 22f, 2.2f, fill)
    }

    /** L'angle du bras pour la n-ième recherche : une piste au hasard entre le bord et le moyeu. */
    private fun angle(n: Int): Float {
        var h = n * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1103515245
        val u = ((h xor (h ushr 16)) and 0xFFFF) / 65535f
        return ARM_MIN + (ARM_MAX - ARM_MIN) * u
    }

    private companion object {
        const val CASE_L = 82f
        const val CASE_R = 288f
        const val CASE_T = 60f
        const val CASE_B = 316f
        /** Le plateau. */
        const val PX = 176f
        const val PY = 150f
        const val PLATTER = 84f
        /** Le pivot du bras, sa longueur, ses angles extrêmes (bord du plateau, près du moyeu). */
        const val AX = 252f
        const val AY = 272f
        const val ARM_L = 126f
        const val ARM_MIN = 24f
        const val ARM_MAX = 47f
        const val SEEK = .9f
        const val HUB = 1
        const val ARM = 2
    }
}
