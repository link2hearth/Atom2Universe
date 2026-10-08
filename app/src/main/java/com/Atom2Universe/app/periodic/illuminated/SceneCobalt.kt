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

/**
 * Cobalt — le bleu de la porcelaine. Un oxyde de cobalt, peint sur la porcelaine crue, devient
 * à la cuisson ce bleu profond qui ne pâlit jamais. Le grand vase blanc tourne lentement sur sa
 * sellette : les feuilles de bananier du col, le rinceau de l'épaule, les pivoines de la panse
 * et les lotus du pied passent devant nous en suivant la courbe du vase.
 */
internal class SceneCobalt : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_blue_vase
    override val noteRes = R.string.card_note_blue_vase
    override val explainRes = R.string.card_explain_blue_vase

    /** Le rayon du vase à chaque hauteur, de [TOP] à [FOOT], interpolé entre quelques cotes. */
    private val radius = FloatArray((FOOT - TOP).toInt() + 1)
    private val vase = Path()
    private val flower = Path()
    private val leaf = Path()
    private val banana = Path()
    private val lotus = Path()

    init {
        val ys = floatArrayOf(TOP, 142f, 156f, 176f, 200f, 226f, 252f, 274f, 284f, FOOT)
        val rs = floatArrayOf(21f, 15f, 13f, 26f, 44f, 46f, 36f, 24f, 22f, 25f)
        for (i in radius.indices) {
            val y = TOP + i
            var k = 0
            while (k < ys.size - 2 && y > ys[k + 1]) k++
            val u = ((y - ys[k]) / (ys[k + 1] - ys[k])).coerceIn(0f, 1f)
            // Catmull-Rom entre les cotes voisines : un galbe sans cassure.
            val p0 = rs[(k - 1).coerceAtLeast(0)]; val p1 = rs[k]; val p2 = rs[k + 1]; val p3 = rs[(k + 2).coerceAtMost(rs.size - 1)]
            radius[i] = .5f * (2f * p1 + (-p0 + p2) * u + (2f * p0 - 5f * p1 + 4f * p2 - p3) * u * u + (-p0 + 3f * p1 - 3f * p2 + p3) * u * u * u)
        }
        vase.moveTo(CX - radius[0], TOP)
        for (i in radius.indices) vase.lineTo(CX - radius[i], TOP + i)
        for (i in radius.indices.reversed()) vase.lineTo(CX + radius[i], TOP + i)
        vase.close()
        // Une pivoine : six pétales autour d'un cœur ; une feuille pointue. Taille unité 1.
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3f
            flower.addOval(cos(a) * .55f - .38f, sin(a) * .55f - .26f, cos(a) * .55f + .38f, sin(a) * .55f + .26f, Path.Direction.CW)
        }
        leaf.moveTo(-1f, 0f); leaf.quadTo(0f, -.5f, 1f, 0f); leaf.quadTo(0f, .5f, -1f, 0f); leaf.close()
        // La feuille de bananier du col, debout ; le pétale de lotus du pied, en contour.
        banana.moveTo(0f, -1f); banana.quadTo(.5f, 0f, 0f, 1f); banana.quadTo(-.5f, 0f, 0f, -1f); banana.close()
        lotus.moveTo(-1f, 1f); lotus.lineTo(-1f, -.1f); lotus.quadTo(-1f, -.8f, 0f, -1.2f)
        lotus.quadTo(1f, -.8f, 1f, -.1f); lotus.lineTo(1f, 1f)
    }

    private fun r(y: Float) = radius[(y - TOP).toInt().coerceIn(0, radius.size - 1)]

    override fun engrave(b: Burin) {
        studio(b)
        // La sellette tournante sous le vase.
        b.body(rect(CX - 8f, TABLE - 6f, CX + 8f, TABLE + 4f), 0xFF5A5E62.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.body(ellipse(CX, FOOT + 2f, 58f, 9f), 0xFF8A9096.toInt(), .3f, 0f, outline = 1f, washAlpha = 255)
        b.body(ellipse(CX, FOOT, 58f, 8f), 0xFFC8CCD0.toInt(), .2f, 0f, outline = 1f, washAlpha = 255)
        // Le vase : la porcelaine blanche, un peu bleutée dans l'ombre.
        b.wash(vase, 0xFFF8F6F0.toInt(), 255)
        // Les filets bleus du col, de l'épaule et du pied : ils font le tour, la rotation ne les change pas.
        for (y in floatArrayOf(138f, 160f, 172f, 246f, 278f, 282f)) {
            val w = r(y)
            b.c.drawArc(CX - w, y - w * .12f, CX + w, y + w * .12f, 0f, 180f, false, b.pen(COBALT, 1.6f))
        }
        b.stroke(vase, 1.1f)
        // Le godet de bleu et le pinceau, sur la table.
        b.body(ellipse(264f, TABLE + 8f, 20f, 5f), 0xFFF4F2EC.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
        b.body(ellipse(264f, TABLE + 7f, 14f, 3f), COBALT, .3f, 0f, outline = .5f, washAlpha = 255)
        b.line(86f, TABLE + 12f, 130f, TABLE + 2f, 2.4f, 0xFF8A5A2A.toInt())
        b.line(86f, TABLE + 12f, 130f, TABLE + 2f, 1f, 0xFFC8904A.toInt())
        b.taper(130f, TABLE + 2f, 134f, TABLE + 1f, 138f, TABLE, 142f, TABLE - 1f, 3.4f, COBALT)
    }

    /** L'atelier : le mur chaulé, l'étagère d'assiettes bleues, la table de bois. */
    private fun studio(b: Burin) {
        val wall = rect(40f, 50f, 320f, TABLE)
        b.wash(wall, 0xFFE4DCCC.toInt(), 255)
        b.glow(276f, 100f, 170f, 0xFFFFF6E0.toInt(), 170)
        b.washGradient(wall, 0xFF6A5E4E.toInt(), 0f, 100f, 0, 0f, TABLE, 120)
        b.hatchRect(RectF(40f, 50f, 320f, TABLE), 90f, 2.8f, .4f, Ink.BROWN, Fade(0f, 70f, 0f, TABLE, 60, 170))
        // L'étagère et ses assiettes posées debout.
        b.body(rect(40f, 120f, 320f, 126f), 0xFF8A6238.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        for (x in floatArrayOf(66f, 104f, 256f, 294f)) {
            val plate = ellipse(x, 102f, 17f, 17f)
            b.body(plate, 0xFFF6F4EE.toInt(), .2f, 0f, outline = .9f, washAlpha = 255)
            b.c.drawCircle(x, 102f, 13.5f, b.pen(COBALT, 1.2f))
            b.c.drawCircle(x, 102f, 6f, b.pen(withAlpha(COBALT, 220)))
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                b.c.drawCircle(x + cos(a) * 10f, 102f + sin(a) * 10f, 1.6f, b.pen(COBALT))
            }
        }
        val table = rect(40f, TABLE, 320f, 340f)
        b.wash(table, 0xFF9A6A3E.toInt(), 255)
        b.hatchRect(RectF(40f, TABLE, 320f, 340f), 0f, 2.2f, .45f, Ink.SEPIA, Fade(0f, TABLE, 0f, 330f, 80, 220))
        b.line(40f, TABLE, 320f, TABLE, 1f)
    }

    // ───────────── animation ─────────────

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(CX - 46f, 0f, CX + 46f, 0f,
            intArrayOf(0x00000000, 0x00000000, 0x30182040, 0x80101830.toInt()), floatArrayOf(0f, .45f, .75f, 1f), Shader.TileMode.CLAMP)
    }
    private val vine = FloatArray(VINE_N * 4)

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val rot = t * SPIN
        c.save(); c.clipPath(vase)
        // Les motifs posés sur la panse : chacun se rétrécit en approchant du bord.
        for (m in MOTIFS) {
            val phi = m[0] + rot
            val face = cos(phi)
            if (face < .04f) continue
            val y = m[1]
            val x = CX + r(y) * sin(phi)
            c.save(); c.translate(x, y); c.rotate(m[3] * face); c.scale(m[2] * face, m[2])
            paint.color = COBALT
            when (m[4]) {
                0f -> {
                    c.drawPath(flower, paint)
                    // Les pétales se séparent d'un trait de blanc, le cœur reste blanc.
                    line.color = 0xFFF8F6F0.toInt(); line.strokeWidth = .08f
                    for (k in 0 until 6) {
                        val a = (k + .5f) * PI.toFloat() / 3f
                        c.drawLine(cos(a) * .3f, sin(a) * .3f, cos(a) * .85f, sin(a) * .85f, line)
                    }
                    paint.color = 0xFFF8F6F0.toInt()
                    c.drawCircle(0f, 0f, .22f, paint)
                    paint.color = COBALT
                    c.drawCircle(0f, 0f, .1f, paint)
                }
                1f -> c.drawPath(leaf, paint)
                2f -> c.drawPath(banana, paint)
                else -> {
                    line.color = COBALT; line.strokeWidth = 1.3f / m[2]
                    c.drawPath(lotus, line)
                    c.drawCircle(0f, .2f, .28f, paint)
                }
            }
            c.restore()
        }
        // Le rinceau de l'épaule et les vagues sous le col : deux lignes continues qui tournent avec le vase.
        band(c, rot, 188f, 5f, 6f, 1.6f)
        band(c, rot, 166f, 2.4f, 10f, 1.2f)
        // L'ombre du côté droit et le reflet de la fenêtre, fixes : seul le décor tourne.
        c.drawRect(CX - 50f, TOP, CX + 50f, FOOT, shade)
        paint.color = withAlpha(0xFFFFFFFF.toInt(), 170)
        c.drawRoundRect(CX + 18f, 196f, CX + 23f, 240f, 2.5f, 2.5f, paint)
        c.drawRoundRect(CX + 7f, 146f, CX + 9f, 158f, 1f, 1f, paint)
        c.restore()
        // Les repères de la sellette, qui tournent avec elle.
        for (k in 0 until 8) {
            val phi = k * PI.toFloat() / 4f + rot
            if (sin(phi) < 0f) continue
            val x = CX + 56f * cos(phi)
            val y = FOOT + 6f * sin(phi) + 1.6f
            paint.color = 0xFF3A3E42.toInt()
            c.drawCircle(x, y, 1.3f, paint)
        }
    }

    /** Une frise qui fait le tour : à la hauteur [y0], ondulant de [amp], [waves] ondulations par tour. */
    private fun band(c: Canvas, rot: Float, y0: Float, amp: Float, waves: Float, w: Float) {
        var n = 0
        var px = 0f; var py = 0f
        for (i in 0..VINE_N) {
            val phi = -1.52f + 3.04f * i / VINE_N
            val alpha = phi - rot
            val y = y0 + amp * sin(waves * alpha)
            val x = CX + r(y) * sin(phi)
            if (i > 0) { vine[n * 4] = px; vine[n * 4 + 1] = py; vine[n * 4 + 2] = x; vine[n * 4 + 3] = y; n++ }
            px = x; py = y
        }
        line.color = COBALT; line.strokeWidth = w
        c.drawLines(vine, 0, n * 4, line)
    }

    private companion object {
        const val CX = 180f
        const val TOP = 134f
        const val FOOT = 288f
        const val TABLE = 286f
        const val COBALT = 0xFF1E3A8A.toInt()
        /** Rotation du vase, en radians par seconde. */
        const val SPIN = .35f
        const val VINE_N = 48
        /** Les motifs : angle sur le vase, hauteur, taille, inclinaison, puis 0 pivoine, 1 feuille, 2 bananier, 3 lotus. */
        val MOTIFS = run {
            val list = ArrayList<FloatArray>()
            val sixth = PI.toFloat() / 3f
            for (k in 0 until 6) {
                val a = k * sixth
                list += floatArrayOf(a, 218f + 5f * (k % 2), 17f - 2f * (k % 2), 0f, 0f)
                list += floatArrayOf(a + .5f, 204f, 10f, -32f, 1f)
                list += floatArrayOf(a + .52f, 234f, 10f, 28f, 1f)
                list += floatArrayOf(a + .25f, 148f, 9f, 0f, 2f)
                list += floatArrayOf(a + .78f, 148f, 9f, 0f, 2f)
            }
            for (k in 0 until 12) list += floatArrayOf(k * PI.toFloat() / 6f, 263f, 8f, 0f, 3f)
            list.toTypedArray()
        }
    }
}
