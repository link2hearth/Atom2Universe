package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Iode — la vapeur violette. Chauffés, les cristaux d'iode, presque noirs, passent directement
 * en vapeur sans fondre, une vapeur d'un violet profond. Sous la hotte du laboratoire, un bécher
 * chauffe sur sa plaque : la vapeur monte en volutes jusqu'au ballon rempli de glace posé dessus,
 * et sur sa paroi froide elle redevient cristaux, de fines paillettes qui scintillent.
 */
internal class SceneIodine : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_violet_vapour
    override val noteRes = R.string.card_note_violet_vapour
    override val explainRes = R.string.card_explain_violet_vapour

    private val glintX = FloatArray(GLINTS); private val glintY = FloatArray(GLINTS)

    init {
        // Les paillettes déposées sous le ballon : sur la calotte basse de la sphère.
        val rnd = Random(53)
        for (k in 0 until GLINTS) {
            val a = (.3f + .4f * rnd.nextFloat()) * PI.toFloat()
            glintX[k] = FX + (FR - 2f) * cos(a); glintY[k] = FY + (FR - 2f) * sin(a)
        }
    }

    override fun engrave(b: Burin) {
        // L'intérieur de la hotte : le fond sombre, les montants, la vitre relevée en haut.
        val back = rect(40f, 50f, 320f, BENCH)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, BENCH,
            intArrayOf(0xFF4A5A5A.toInt(), 0xFF2A3434.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(back, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, BENCH), 90f, 3.2f, .35f, 0xFF1A2424.toInt(), Fade(0f, 50f, 0f, BENCH, 60, 180))
        b.body(rect(40f, 50f, 60f, BENCH), 0xFFB8BCB8.toInt(), .35f, 90f, outline = .9f, washAlpha = 255)
        b.body(rect(300f, 50f, 320f, BENCH), 0xFFB8BCB8.toInt(), .35f, 90f, outline = .9f, washAlpha = 255)
        b.wash(rect(60f, 50f, 300f, 84f), 0xFFC8DCE0.toInt(), 70)
        b.body(rect(60f, 82f, 300f, 90f), 0xFFC8CCC8.toInt(), .25f, 0f, outline = .9f, washAlpha = 255)
        b.line(64f, 56f, 120f, 78f, 1.4f, withAlpha(0xFFFFFFFF.toInt(), 90))
        // La paillasse claire, la plaque chauffante, son bouton et son témoin.
        b.wash(rect(40f, BENCH, 320f, 340f), 0xFFD8D4C8.toInt(), 255)
        b.hatchRect(RectF(40f, BENCH, 320f, 340f), 0f, 2.6f, .35f, 0xFF7A766A.toInt(), Fade(0f, BENCH, 0f, 330f, 60, 160))
        b.fill(ellipse(180f, BENCH + 2f, 76f, 5f), withAlpha(0xFF1A140E.toInt(), 110))
        val plate = Path().apply { addRoundRect(110f, PLATE_T, 250f, BENCH, 5f, 5f, Path.Direction.CW) }
        b.body(plate, 0xFFE8E4DC.toInt(), .25f, 0f, Fade(0f, BENCH, 0f, PLATE_T, 180, 20), outline = 1f, washAlpha = 255)
        b.body(rect(114f, PLATE_T - 5f, 246f, PLATE_T + 1f), 0xFF4A4E54.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        b.body(ellipse(130f, PLATE_T + 14f, 6f, 6f), 0xFF2A2A2E.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        b.line(130f, PLATE_T + 14f, 130f, PLATE_T + 9f, 1.2f, 0xFFE8E8E8.toInt())
        // Le bécher : sa paroi du fond, les cristaux d'iode au fond, le bec.
        b.wash(rect(BL, BT, BR, BB), 0xFFDDEEF2.toInt(), 60)
        val rnd = Random(9)
        repeat(70) {
            val x = BL + 6f + rnd.nextFloat() * (BR - BL - 12f)
            val y = BB - 3f - rnd.nextFloat() * rnd.nextFloat() * 12f
            val s = 1.6f + rnd.nextFloat() * 2.6f
            val a = rnd.nextFloat() * PI.toFloat()
            val flake = poly(x + s * cos(a), y + s * sin(a) * .5f, x + s * .4f * cos(a + 1.8f), y + s * .6f * sin(a + 1.8f),
                x - s * cos(a), y - s * sin(a) * .5f, x + s * .4f * cos(a + 4.4f), y + s * .6f * sin(a + 4.4f))
            b.fill(flake, if (rnd.nextFloat() < .3f) 0xFF4A3A5A.toInt() else 0xFF1E1A24.toInt())
        }
        b.line(BL - 2f, BT, BR + 2f, BT, 1.6f, 0xFF6A8A96.toInt())
    }

    override fun sprites(): List<Sprite> = listOf(
        // Le ballon de verre, plein d'eau glacée ; sa calotte couverte de paillettes d'iode.
        Sprite(FLASK, RectF(FX - FR - 4f, 92f, FX + FR + 4f, FY + FR + 4f)) { b ->
            val neck = rect(FX - 10f, 96f, FX + 10f, FY - FR + 8f)
            val sphere = ellipse(FX, FY, FR, FR)
            val glass = Path().apply { op(neck, sphere, Path.Op.UNION) }
            b.wash(glass, 0xFFE4F2F6.toInt(), 120)
            // L'eau et la glace.
            val water = Path().apply { op(sphere, rect(FX - FR, FY - FR * .55f, FX + FR, FY + FR), Path.Op.INTERSECT) }
            b.washGradient(water, 0xFF8AC0D8.toInt(), 0f, FY - FR * .55f, 120, 0f, FY + FR, 200)
            for ((x, y, r) in arrayOf(Triple(FX - 18f, FY - 14f, 11f), Triple(FX + 12f, FY - 18f, 10f), Triple(FX - 2f, FY - 2f, 12f), Triple(FX + 22f, FY + 4f, 9f))) {
                val cube = Path().apply { addRoundRect(x - r, y - r, x + r, y + r, 3f, 3f, Path.Direction.CW) }
                b.wash(cube, 0xFFFFFFFF.toInt(), 150)
                b.stroke(cube, .6f, withAlpha(0xFF5A8AA8.toInt(), 200))
                b.line(x - r + 3f, y - r + 3f, x - r + 3f, y + r - 5f, .8f, withAlpha(0xFFFFFFFF.toInt(), 230))
            }
            b.line(FX - FR * .8f, FY - FR * .55f, FX + FR * .8f, FY - FR * .55f, 1f, withAlpha(0xFFFFFFFF.toInt(), 200))
            // La calotte froide, tapissée de paillettes d'iode.
            val cap = Path().apply { op(sphere, ellipse(FX, FY + FR + 10f, FR * .95f, 22f), Path.Op.INTERSECT) }
            b.wash(cap, 0xFF3A2A4A.toInt(), 150)
            val rnd = Random(3)
            repeat(80) {
                val a = (.12f + .76f * rnd.nextFloat()) * PI.toFloat()
                val r = FR - 1f - rnd.nextFloat() * 7f
                val x = FX + r * cos(a); val y = FY + r * sin(a)
                if (y < FY + FR - 14f) return@repeat
                b.line(x, y, x + (rnd.nextFloat() - .5f) * 4f, y - 1.5f - rnd.nextFloat() * 3f, .9f, if (rnd.nextFloat() < .4f) 0xFF6A4A8A.toInt() else 0xFF1E1A24.toInt())
            }
            // Le verre : contour, reflets.
            b.stroke(glass, 1.2f, 0xFF4A6A76.toInt())
            b.c.drawArc(FX - FR + 6f, FY - FR + 6f, FX + FR - 6f, FY + FR - 6f, 200f, 50f, false, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200), 2.2f))
            b.line(FX - 6f, 100f, FX - 6f, FY - FR + 2f, 1.2f, withAlpha(0xFFFFFFFF.toInt(), 170))
            b.body(rect(FX - 12f, 92f, FX + 12f, 98f), 0xFFE4F2F6.toInt(), 0f, outline = .9f, washAlpha = 200)
        }
    )

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        // La plaque chauffe : son témoin rouge, sa surface qui rougeoie un peu.
        fill.color = withAlpha(0xFFFF4A2A.toInt(), 150 + (60 * sin(t * 3f)).toInt())
        c.drawCircle(230f, PLATE_T + 14f, 2.6f, fill)
        fill.color = withAlpha(0xFFFF6A2A.toInt(), 40 + (20 * sin(t * 1.3f)).toInt())
        c.drawRect(BL, PLATE_T - 5f, BR, PLATE_T - 2f, fill)
        // La vapeur violette : des volutes qui montent des cristaux vers le ballon et s'y éteignent.
        c.save(); c.clipRect(BL + 1.5f, BT - 30f, BR - 1.5f, BB)
        for (k in 0 until PUFFS) {
            val period = 3f + 1.4f * hash(k, 1)
            val off = hash(k, 2) * period
            val n = ((t + off) / period).toInt()
            val life = ((t + off) / period - n)
            val x0 = BL + 10f + (BR - BL - 20f) * hash(n, k)
            val swirl = 10f * sin(life * 5f + k) * life
            val x = x0 + swirl + (FX - x0) * life * .35f
            val y = BB - 6f - life * (BB - 6f - (FY + FR - 6f))
            val a = sin(life * PI.toFloat()) * (1f - life * .4f)
            fill.color = withAlpha(0xFF8A2AC8.toInt(), (46 * a).toInt())
            c.drawCircle(x, y, 7f + 12f * life, fill)
        }
        // Un voile violet plus dense au fond, qui respire.
        fill.color = withAlpha(0xFF6A1AA8.toInt(), 50 + (16 * sin(t * .9f)).toInt())
        c.drawRect(BL, BB - 22f, BR, BB, fill)
        c.restore()
        // Le ballon glacé, posé sur le bécher.
        s.draw(c, FLASK)
        // Les paillettes neuves scintillent sous la calotte froide.
        for (k in 0 until GLINTS) {
            val g = sin(t * (1.6f + hash(k, 5)) + k * 2.3f)
            if (g < .7f) continue
            val e = (g - .7f) / .3f
            ink.color = withAlpha(0xFFE8D8FF.toInt(), (230 * e).toInt()); ink.strokeWidth = .8f
            val r = 3.4f * e
            c.drawLine(glintX[k] - r, glintY[k], glintX[k] + r, glintY[k], ink)
            c.drawLine(glintX[k], glintY[k] - r, glintX[k], glintY[k] + r, ink)
        }
        // Les parois avant du bécher et son bord, par-dessus la vapeur et le bas du ballon.
        ink.color = 0xFF4A6A76.toInt(); ink.strokeWidth = 1.6f
        c.drawLine(BL - 2f, BT, BR + 2f, BT, ink)
        ink.strokeWidth = 1.4f
        c.drawLine(BL, BT, BL, BB, ink); c.drawLine(BR, BT, BR, BB, ink); c.drawLine(BL, BB, BR, BB, ink)
        ink.color = withAlpha(0xFFFFFFFF.toInt(), 150); ink.strokeWidth = 1.6f
        c.drawLine(BL + 5f, BT + 8f, BL + 5f, BB - 8f, ink)
        ink.strokeWidth = 1f
        for (k in 1..3) { ink.color = withAlpha(0xFF4A6A76.toInt(), 160); c.drawLine(BR - 14f, BB - k * 18f, BR - 4f, BB - k * 18f, ink) }
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val BENCH = 300f
        const val PLATE_T = 280f
        /** Le bécher. */
        const val BL = 130f
        const val BR = 230f
        const val BT = 192f
        const val BB = 274f
        /** Le ballon : centre et rayon de la sphère. */
        const val FX = 180f
        const val FY = 168f
        const val FR = 44f
        const val PUFFS = 34
        const val GLINTS = 16
        const val FLASK = 1
    }
}
