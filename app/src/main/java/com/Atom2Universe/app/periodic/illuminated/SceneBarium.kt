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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Baryum — la radio au baryum. Le sulfate de baryum arrête les rayons X : bu en boisson blanche,
 * il dessine le tube digestif sur l'écran. La gorgée descend l'œsophage, remplit l'estomac sous
 * sa bulle d'air, les ondes de l'estomac la brassent puis la poussent dans l'intestin.
 */
internal class SceneBarium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_barium_xray
    override val noteRes = R.string.card_note_barium_xray
    override val explainRes = R.string.card_explain_barium_xray

    override fun engrave(b: Burin) {
        // L'écran de radioscopie : noir bleuté, le corps un peu plus clair.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            0xFF0A1015.toInt(), 0xFF121A20.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 340f, b.p)
        val torso = Path().apply {
            moveTo(40f, 96f); quadTo(100f, 70f, 150f, 64f); lineTo(210f, 64f); quadTo(260f, 70f, 320f, 96f)
            lineTo(320f, 150f); quadTo(290f, 240f, 286f, 340f); lineTo(74f, 340f); quadTo(70f, 240f, 40f, 150f); close()
        }
        b.wash(torso, 0xFF2E3A42.toInt(), 255)
        // Les poumons, pleins d'air, sont noirs ; le cœur les entame à droite de l'image.
        val left = Path().apply { moveTo(162f, 96f); quadTo(118f, 90f, 94f, 120f); quadTo(72f, 166f, 72f, 226f); quadTo(120f, 214f, 164f, 200f); close() }
        val right = Path().apply { moveTo(198f, 96f); quadTo(242f, 90f, 266f, 120f); quadTo(288f, 166f, 288f, 226f); quadTo(240f, 214f, 196f, 200f); close() }
        b.wash(left, 0xFF070B0F.toInt(), 215); b.wash(right, 0xFF070B0F.toInt(), 215)
        b.wash(ellipse(206f, 184f, 40f, 32f), 0xFF34404A.toInt(), 235)
        // Les clavicules et les côtes, de chaque côté de la colonne.
        for (side in SIDES) {
            b.c.drawPath(Path().apply { moveTo(180f + side * 6f, 84f); quadTo(180f + side * 50f, 66f, 180f + side * 114f, 76f) }, b.pen(withAlpha(BONE, 120), 7f))
            for (i in 0 until 10) {
                val y0 = 74f + i * 21f
                val reach = 118f - i * 4f
                b.c.drawPath(Path().apply {
                    moveTo(180f + side * 12f, y0)
                    cubicTo(180f + side * 50f, y0 - 14f, 180f + side * reach, y0 - 6f, 180f + side * (reach + 4f), y0 + 26f)
                }, b.pen(withAlpha(BONE, 95), 5.5f))
                b.c.drawPath(Path().apply {
                    moveTo(180f + side * (reach + 4f), y0 + 26f)
                    quadTo(180f + side * (reach - 6f), y0 + 52f, 180f + side * (reach - 50f), y0 + 62f)
                }, b.pen(withAlpha(BONE, 40), 4f))
            }
        }
        // La colonne : une pile de vertèbres.
        var y = 64f
        while (y < 336f) {
            b.wash(Path().apply { addRoundRect(170f, y, 190f, y + 13f, 4f, 4f, Path.Direction.CW) }, BONE, 125)
            b.wash(ellipse(173f, y + 6f, 2.6f, 3.4f), BONE, 90); b.wash(ellipse(187f, y + 6f, 2.6f, 3.4f), BONE, 90)
            y += 17f
        }
        // Lignes de balayage et coins assombris de l'écran.
        b.hatchRect(RectF(40f, 50f, 320f, 340f), 0f, 3f, .4f, withAlpha(0xFF000000.toInt(), 60))
        b.pen(0xFF000000.toInt()).shader = RadialGradient(180f, 190f, 200f,
            intArrayOf(0x00000000, 0x00000000, 0xC0000000.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 340f, b.p)
    }

    // ───────────── animation ─────────────

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tube = Path()
    private val stomach = Path()
    private val ox = FloatArray(SX.size * 2)
    private val oy = FloatArray(SX.size * 2)
    private val heart = RectF()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val tc = t % CYCLE
        val g = 1f - smooth((tc - 7.1f) / .8f)
        // Le grain de l'image vivante.
        val frame = (t * 12f).toInt()
        fill.color = withAlpha(BONE, 34)
        for (k in 0 until 60) c.drawCircle(46f + 268f * hash(frame, k), 60f + 258f * hash(frame, k + 97), .8f, fill)
        // Le bord du cœur bat.
        val beat = max(0f, sin(t * 7.2f))
        heart.set(164f - beat * 2f, 150f - beat * 1.5f, 248f + beat * 2f, 218f + beat * 1.5f)
        line.color = withAlpha(BONE, 55); line.strokeWidth = 3f
        c.drawArc(heart, -70f, 150f, false, line)

        // L'estomac : sa ligne médiane, élargie, pincée par une onde qui court vers la sortie.
        val wave = (t % WAVE) / WAVE * 1.8f - .4f
        val n = SX.size
        for (i in 0 until n) {
            val u = i / (n - 1f)
            val d = (u - wave) / .13f
            val w = SW[i] * (1f - .32f * exp(-d * d) * smooth((u - .25f) / .2f))
            val a = if (i == 0) 0 else i - 1
            val z = if (i == n - 1) n - 1 else i + 1
            val len = hypot(SX[z] - SX[a], SY[z] - SY[a])
            val tx = (SX[z] - SX[a]) / len; val ty = (SY[z] - SY[a]) / len
            ox[i] = SX[i] - ty * w; oy[i] = SY[i] + tx * w
            ox[2 * n - 1 - i] = SX[i] + ty * w; oy[2 * n - 1 - i] = SY[i] - tx * w
        }
        val m = 2 * n
        stomach.reset()
        stomach.moveTo((ox[m - 1] + ox[0]) / 2f, (oy[m - 1] + oy[0]) / 2f)
        for (k in 0 until m) {
            val k2 = (k + 1) % m
            stomach.quadTo(ox[k], oy[k], (ox[k] + ox[k2]) / 2f, (oy[k] + oy[k2]) / 2f)
        }
        stomach.close()
        line.color = withAlpha(BONE, 40); line.strokeWidth = 1.2f
        c.drawPath(stomach, line)

        // Le baryum : il remplit l'estomac par le bas, la bulle d'air reste au-dessus.
        val full = smooth((tc - 1f) / 2.2f)
        val level = 316f - 84f * full + 14f * smooth((tc - 4.2f) / 2.6f)
        if (full > 0f && g > 0f) {
            c.save(); c.clipRect(0f, 0f, 360f, level)
            fill.color = withAlpha(0xFF03070A.toInt(), (130 * full * g).toInt()); c.drawPath(stomach, fill)
            c.restore()
            c.save(); c.clipRect(0f, level, 360f, 400f)
            fill.color = withAlpha(BARIUM, (226 * g).toInt()); c.drawPath(stomach, fill)
            c.restore()
        }
        // La gorgée dans l'œsophage, puis le filet qui tombe dans l'estomac.
        val head = E_LEN * ((tc - .2f) / 1f).coerceIn(0f, 1f)
        val tail = E_LEN * ((tc - 1f) / 1.6f).coerceIn(0f, 1f)
        if (head - tail > .5f) {
            partial(EX, EY, E_CUM, tail, head)
            glow(c, 8f, g)
        }
        val stream = smooth((tc - 1.15f) / .15f) * (1f - smooth((tc - 2.5f) / .25f))
        if (stream > 0f) {
            line.color = withAlpha(BARIUM, (200 * stream * g).toInt()); line.strokeWidth = 4f
            c.drawLine(210f, 214f, 213f, level, line)
        }
        // L'intestin se dessine à mesure que l'estomac se vide.
        val gone = D_LEN * smooth((tc - 3.6f) / 2.8f)
        if (gone > .5f) {
            partial(DX, DY, D_CUM, 0f, gone)
            glow(c, 7f, g)
        }
    }

    private fun glow(c: Canvas, width: Float, g: Float) {
        line.color = withAlpha(BARIUM, (50 * g).toInt()); line.strokeWidth = width * 1.9f
        c.drawPath(tube, line)
        line.color = withAlpha(BARIUM, (226 * g).toInt()); line.strokeWidth = width
        c.drawPath(tube, line)
    }

    /** Le morceau de la ligne brisée entre les longueurs [from] et [to], dans [tube]. */
    private fun partial(xs: FloatArray, ys: FloatArray, cum: FloatArray, from: Float, to: Float) {
        tube.reset()
        var started = false
        for (i in 0 until xs.size - 1) {
            val s0 = cum[i]; val s1 = cum[i + 1]
            if (s1 < from || s0 > to) continue
            val seg = s1 - s0
            if (!started) {
                val f = ((from - s0) / seg).coerceIn(0f, 1f)
                tube.moveTo(xs[i] + (xs[i + 1] - xs[i]) * f, ys[i] + (ys[i + 1] - ys[i]) * f)
                started = true
            }
            val e = ((to - s0) / seg).coerceIn(0f, 1f)
            tube.lineTo(xs[i] + (xs[i + 1] - xs[i]) * e, ys[i] + (ys[i + 1] - ys[i]) * e)
        }
    }

    private fun smooth(x: Float): Float {
        val k = x.coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val CYCLE = 8f
        const val WAVE = 2.4f
        const val BONE = 0xFFB8C4CA.toInt()
        const val BARIUM = 0xFFEEF3F4.toInt()
        val SIDES = floatArrayOf(-1f, 1f)
        // L'œsophage, l'estomac en J (de la bulle du haut à la sortie), puis le début de l'intestin.
        val EX = floatArrayOf(178f, 179f, 182f, 187f, 196f, 208f)
        val EY = floatArrayOf(56f, 100f, 145f, 180f, 202f, 212f)
        val SX = floatArrayOf(222f, 224f, 230f, 234f, 233f, 227f, 213f, 198f, 186f)
        val SY = floatArrayOf(186f, 197f, 214f, 238f, 262f, 282f, 295f, 299f, 297f)
        val SW = floatArrayOf(6f, 15f, 21f, 22f, 21f, 18f, 14f, 9f, 5f)
        val DX = floatArrayOf(186f, 174f, 162f, 158f, 166f, 184f, 206f)
        val DY = floatArrayOf(298f, 293f, 300f, 314f, 328f, 333f, 328f)
        val E_CUM = lengths(EX, EY)
        val D_CUM = lengths(DX, DY)
        val E_LEN = E_CUM[E_CUM.size - 1]
        val D_LEN = D_CUM[D_CUM.size - 1]

        fun lengths(xs: FloatArray, ys: FloatArray): FloatArray {
            val out = FloatArray(xs.size)
            for (i in 1 until xs.size) out[i] = out[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            return out
        }
    }
}
