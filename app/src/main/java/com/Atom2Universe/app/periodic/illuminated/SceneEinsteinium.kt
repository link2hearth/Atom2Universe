package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Einsteinium — Einstein au tableau. L'élément porte son nom. Le savant aux cheveux blancs écrit
 * à la craie la formule la plus célèbre de la physique en longeant le tableau, recule pour la
 * regarder, puis l'efface et recommence.
 */
internal class SceneEinsteinium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_einstein_board
    override val noteRes = R.string.card_note_einstein_board
    override val explainRes = R.string.card_explain_einstein_board

    override fun engrave(b: Burin) {
        // Le mur de la salle et le tableau noir encadré de bois.
        b.body(rect(40f, 50f, 320f, 340f), 0xFFD8C8A8.toInt(), .2f, 90f, outline = 0f, washAlpha = 255)
        b.body(rect(122f, 64f, 320f, 242f), 0xFF8A5A30.toInt(), .3f, 0f, outline = .9f, washAlpha = 255)
        val board = rect(BL, BT, BR, BB)
        b.wash(board, 0xFF1E3028.toInt(), 255)
        b.stipple(board, 300, .8f, withAlpha(Ink.WHITE, 26), 2)
        // La trace d'un vieux coup de chiffon.
        b.c.drawPath(Path().apply { moveTo(150f, 200f); quadTo(200f, 190f, 260f, 204f) }, b.pen(withAlpha(Ink.WHITE, 30), 9f))
        // L'auget et deux bâtons de craie.
        b.body(rect(122f, 236f, 320f, 244f), 0xFF8A5A30.toInt(), .3f, 0f, outline = .8f, washAlpha = 255)
        b.line(196f, 235f, 210f, 235f, 2.4f, Ink.WHITE)
        b.line(280f, 235f, 292f, 235f, 2.4f, 0xFFE8D870.toInt())
        // Le parquet.
        b.body(rect(40f, 300f, 320f, 340f), 0xFF7A5030.toInt(), .4f, 0f, outline = .8f, washAlpha = 255)
    }

    override fun sprites() = listOf(
        Sprite(MAN, RectF(36f, 76f, 150f, 334f)) { b ->
            // Le cou, puis le gilet gris sur la chemise blanche.
            b.body(rect(96f, 158f, 110f, 182f), SKIN, .3f, 0f, outline = .7f, washAlpha = 255)
            val body = Path().apply {
                moveTo(66f, 186f); quadTo(96f, 172f, 128f, 184f); lineTo(140f, 330f); lineTo(52f, 330f); close()
            }
            b.body(body, CARDIGAN, .35f, 70f, outline = 1f, washAlpha = 255)
            b.body(poly(92f, 178f, 114f, 178f, 106f, 198f), 0xFFF2EEE4.toInt(), 0f, outline = .7f, washAlpha = 255)
            for (k in 0 until 4) b.c.drawCircle(110f, 210f + k * 20f, 2f, b.pen(0xFF3A3A3E.toInt()))
            // La tête de profil, tournée vers le tableau.
            val head = Path().apply {
                moveTo(96f, 120f); quadTo(122f, 116f, 122f, 136f); lineTo(130f, 146f); lineTo(122f, 149f)
                quadTo(124f, 160f, 116f, 166f); quadTo(100f, 170f, 92f, 160f); quadTo(82f, 140f, 96f, 120f); close()
            }
            b.body(head, SKIN, .2f, 60f, outline = .9f, washAlpha = 255)
            b.line(108f, 124f, 118f, 126f, .6f, Ink.BROWN); b.line(107f, 128f, 117f, 130f, .6f, Ink.BROWN)
            // La crinière blanche, en boucles folles derrière le visage.
            b.wash(ellipse(84f, 120f, 26f, 24f), HAIR, 255)
            val rnd = kotlin.random.Random(5)
            repeat(170) {
                val a = rnd.nextFloat() * 6.2831855f
                val r = rnd.nextFloat()
                val x = 84f + 34f * r * cos(a); val y = 118f + 30f * r * sin(a)
                val curl = 3f + rnd.nextFloat() * 5f
                val start = rnd.nextFloat() * 360f
                val sweep = 200f + rnd.nextFloat() * 120f
                val grey = rnd.nextFloat() < .3f
                if (x > 104f && y > 124f) return@repeat
                b.c.drawArc(RectF(x - curl, y - curl, x + curl, y + curl), start, sweep, false,
                    b.pen(if (grey) 0xFFA8A8A4.toInt() else 0xFFF8F8F4.toInt(), .9f))
            }
            // L'oreille, l'œil, les sourcils et la moustache, blancs et touffus.
            b.body(ellipse(97f, 142f, 4f, 6f), SKIN, .3f, 0f, outline = .7f, washAlpha = 255)
            b.c.drawCircle(116f, 137f, 1.4f, b.pen(Ink.SEPIA))
            for (k in 0 until 6) b.line(110f + k * 1.6f, 132f - (k % 2), 113f + k * 1.6f, 131f, 1.2f, HAIR)
            val stache = Path().apply { moveTo(114f, 150f); quadTo(124f, 146f, 128f, 152f); quadTo(122f, 158f, 114f, 155f); close() }
            b.body(stache, HAIR, 0f, outline = .7f, washAlpha = 255)
            for (k in 0 until 5) b.line(116f + k * 2.4f, 150f, 115f + k * 2.4f, 156f, .5f, 0xFFB8B8B4.toInt())
        },
        Sprite(ERASER, RectF(-14f, -9f, 14f, 9f)) { b ->
            b.body(Path().apply { addRoundRect(-12f, -7f, 12f, 1f, 2f, 2f, Path.Direction.CW) }, 0xFF8A5A30.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
            b.body(rect(-12f, 1f, 12f, 7f), 0xFF5A5A60.toInt(), .3f, 0f, outline = .7f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val chalk = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2.4f }
    private val arm = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tipX = 0f
    private var tipY = 0f

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val tc = t % CYCLE
        // Où est la craie (ou le chiffon), et jusqu'où la formule est écrite.
        val written = TOTAL * smooth01((tc - W0) / (W1 - W0))
        val hx: Float; val hy: Float
        when {
            tc < W0 -> { hx = SEG[0]; hy = SEG[1] }
            tc < W1 -> { at(written); hx = tipX; hy = tipY }
            tc < W1 + .8f -> { at(TOTAL); val k = smooth01((tc - W1) / .8f); hx = tipX + (REST_X - tipX) * k; hy = tipY + (REST_Y - tipY) * k }
            tc < E0 - .3f -> { hx = REST_X; hy = REST_Y }
            tc < E0 -> { val k = smooth01((tc - E0 + .3f) / .3f); hx = REST_X + (SWEEP_A - REST_X) * k; hy = REST_Y + (SWEEP_Y - REST_Y) * k }
            tc < E1 -> {
                val k = (tc - E0) / (E1 - E0)
                hx = SWEEP_A + (SWEEP_B - SWEEP_A) * k; hy = SWEEP_Y + 12f * sin(k * 25.132741f)
            }
            else -> { val k = smooth01((tc - E1) / (CYCLE - E1)); hx = SWEEP_B + (SEG[0] - SWEEP_B) * k; hy = SWEEP_Y + (SEG[1] - SWEEP_Y) * k }
        }

        // Le savant suit sa main le long du tableau ; son bras droit se plie à l'épaule et au coude.
        val dx = ((hx - 150f) * .7f).coerceIn(-10f, 100f)
        val bob = 1.2f * sin(t * 1.4f)
        val sx = SHOULDER_X + dx; val sy = SHOULDER_Y + bob
        val gx = hx - 5f; val gy = hy + 9f
        val d = min(hypot(gx - sx, gy - sy), L1 + L2 - .5f)
        val a = atan2(gy - sy, gx - sx)
        val bend = acos(((L1 * L1 + d * d - L2 * L2) / (2f * L1 * d)).coerceIn(-1f, 1f))
        val ex = sx + L1 * cos(a + bend); val ey = sy + L1 * sin(a + bend)
        val wx = sx + d * cos(a); val wy = sy + d * sin(a)

        // La formule à la craie ; le chiffon l'efface et n'en laisse qu'un voile, qui s'en va.
        when {
            tc < W0 -> Unit
            tc < E0 -> { chalk.color = withAlpha(CHALK, 235); formula(c, written) }
            tc < E1 -> {
                val cut = wx + 12f
                c.save(); c.clipRect(cut, BT, BR, BB)
                chalk.color = withAlpha(CHALK, 235); formula(c, TOTAL)
                c.restore()
                c.save(); c.clipRect(BL, BT, cut, BB)
                chalk.color = withAlpha(CHALK, 40); formula(c, TOTAL)
                c.restore()
            }
            else -> { chalk.color = withAlpha(CHALK, (40 * (1f - (tc - E1) / (CYCLE - E1))).toInt()); formula(c, TOTAL) }
        }

        s.draw(c, MAN, dx, bob)
        arm.color = CARDIGAN; arm.strokeWidth = 15f; c.drawLine(sx, sy, ex, ey, arm)
        arm.strokeWidth = 12f; c.drawLine(ex, ey, wx - 4f * cos(a), wy - 4f * sin(a), arm)
        arm.color = 0xFF3A3A3E.toInt(); arm.strokeWidth = .8f
        c.drawLine(ex, ey, wx - 4f * cos(a), wy - 4f * sin(a), arm)
        // Le chiffon et la craie passent l'un à l'autre en fondu.
        val rag = smooth01((tc - E0 + .3f) / .25f) * (1f - smooth01((tc - E1) / .3f))
        if (rag > 0f) s.draw(c, ERASER, wx + 2f, wy - 4f, alpha = (255 * rag).toInt())
        if (rag < 1f) {
            arm.color = withAlpha(Ink.WHITE, (255 * (1f - rag)).toInt()); arm.strokeWidth = 2.6f
            c.drawLine(wx, wy, wx + 5f, wy - 9f, arm)
        }
        fill.color = SKIN; c.drawCircle(wx, wy, 6f, fill)
        // Un peu de poussière de craie tombe pendant qu'il écrit.
        if (tc > W0 && tc < W1 + 1.1f) for (k in 0 until 6) {
            val born = (((tc - W0) / .2f).toInt() - k) * .2f + W0
            val age = tc - born
            if (age < 0f || age > 1f || born > W1) continue
            at(TOTAL * smooth01((born - W0) / (W1 - W0)))
            fill.color = withAlpha(Ink.WHITE, (180 * sin(age * 3.1415927f)).toInt())
            c.drawCircle(tipX + 4f * sin(age * 7f + k), tipY + 50f * age * age + 2f, 1.1f, fill)
        }
    }

    /** Dessine la formule jusqu'à la longueur [upTo] (les levers de craie ne laissent rien). */
    private fun formula(c: Canvas, upTo: Float) {
        var done = 0f
        var i = 0
        while (i < SEG.size && done < upTo) {
            val x0 = SEG[i]; val y0 = SEG[i + 1]; val x1 = SEG[i + 2]; val y1 = SEG[i + 3]
            val len = hypot(x1 - x0, y1 - y0)
            val f = ((upTo - done) / len).coerceAtMost(1f)
            if (SEG[i + 4] > 0f) c.drawLine(x0, y0, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, chalk)
            done += len
            i += 5
        }
    }

    /** La pointe de la craie après [length] de chemin. Résultat dans ([tipX], [tipY]). */
    private fun at(length: Float) {
        var done = 0f
        var i = 0
        tipX = SEG[0]; tipY = SEG[1]
        while (i < SEG.size) {
            val x0 = SEG[i]; val y0 = SEG[i + 1]; val x1 = SEG[i + 2]; val y1 = SEG[i + 3]
            val len = hypot(x1 - x0, y1 - y0)
            if (done + len >= length) {
                val f = ((length - done) / len).coerceIn(0f, 1f)
                tipX = x0 + (x1 - x0) * f; tipY = y0 + (y1 - y0) * f
                return
            }
            done += len
            tipX = x1; tipY = y1
            i += 5
        }
    }

    private fun smooth01(x: Float): Float {
        val q = x.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    private companion object {
        const val CYCLE = 10f
        // Écriture, puis coup de chiffon (en secondes dans le cycle).
        const val W0 = .4f
        const val W1 = 4.6f
        const val E0 = 7.7f
        const val E1 = 8.9f
        const val BL = 128f
        const val BT = 70f
        const val BR = 314f
        const val BB = 236f
        const val SHOULDER_X = 104f
        const val SHOULDER_Y = 192f
        const val L1 = 60f
        const val L2 = 60f
        const val REST_X = 160f
        const val REST_Y = 244f
        const val SWEEP_A = 132f
        const val SWEEP_B = 290f
        const val SWEEP_Y = 130f
        const val MAN = 1
        const val ERASER = 2
        const val SKIN = 0xFFE8C0A0.toInt()
        const val HAIR = 0xFFF4F4F0.toInt()
        const val CARDIGAN = 0xFF7A7A80.toInt()
        const val CHALK = 0xFFF4F4EC.toInt()
        // E = m c² : les traits à la craie, chacun « x0, y0, x1, y1, tracé », levers compris.
        val SEG = build(
            floatArrayOf(166f, 112f, 146f, 112f, 146f, 148f, 166f, 148f),
            floatArrayOf(146f, 130f, 162f, 130f),
            floatArrayOf(178f, 124f, 196f, 124f),
            floatArrayOf(178f, 136f, 196f, 136f),
            floatArrayOf(208f, 148f, 208f, 128f, 212f, 126f, 216f, 128f, 218f, 134f, 218f, 148f),
            floatArrayOf(218f, 134f, 222f, 127f, 227f, 127f, 230f, 134f, 230f, 148f),
            floatArrayOf(256f, 130f, 251f, 126f, 245f, 127f, 241f, 133f, 241f, 141f, 245f, 147f, 251f, 148f, 256f, 144f),
            floatArrayOf(262f, 112f, 265f, 108f, 270f, 108f, 272f, 112f, 270f, 116f, 262f, 124f, 273f, 124f)
        )
        val TOTAL = run {
            var sum = 0f
            var i = 0
            while (i < SEG.size) { sum += hypot(SEG[i + 2] - SEG[i], SEG[i + 3] - SEG[i + 1]); i += 5 }
            sum
        }

        /** Enchaîne les traits ; entre deux traits, un segment « levé » que la craie parcourt sans écrire. */
        fun build(vararg strokes: FloatArray): FloatArray {
            val out = ArrayList<Float>()
            var lastX = Float.NaN; var lastY = 0f
            for (s in strokes) {
                if (!lastX.isNaN()) out.addAll(listOf(lastX, lastY, s[0], s[1], 0f))
                for (i in 0 until s.size - 2 step 2) out.addAll(listOf(s[i], s[i + 1], s[i + 2], s[i + 3], 1f))
                lastX = s[s.size - 2]; lastY = s[s.size - 1]
            }
            return out.toFloatArray()
        }
    }
}
