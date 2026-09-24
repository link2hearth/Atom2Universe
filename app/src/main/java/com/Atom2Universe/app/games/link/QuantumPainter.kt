package com.Atom2Universe.app.games.link

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le décor d'Intrication : des atomes excités sur une grille quantique. Le plateau et la
 * tuile du hub peignent avec ces mêmes pièces.
 *
 * Un atome montre son énergie par ses **orbites** : une orbite par niveau, chacune avec
 * son électron qui tourne. La couleur suit l'énergie, du bleu calme à l'orange chaud ;
 * au repos, il ne reste qu'un noyau terne.
 */
object QuantumPainter {

    const val SPACE_TOP = 0xFF070B1F.toInt()
    const val SPACE_BOTTOM = 0xFF120A2A.toInt()

    /** Couleur par niveau d'énergie, 0 (repos) à 4. */
    val ENERGY_COLORS = intArrayOf(
        0xFF5A6B9A.toInt(), 0xFF4FC3F7.toInt(), 0xFF69F0AE.toInt(), 0xFFFFD54F.toInt(), 0xFFFF7043.toInt()
    )

    /** Couleur des fils d'onde, une par paire intriquée. */
    val PAIR_COLORS = intArrayOf(
        0xFFE040FB.toInt(), 0xFF40C4FF.toInt(), 0xFFFFAB40.toInt(), 0xFFB2FF59.toInt(), 0xFFFF4081.toInt(),
        0xFF7C4DFF.toInt(), 0xFFE0E0E0.toInt()
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val wavePath = Path()
    private val glowShaders = HashMap<Int, Shader>()

    fun energyColor(e: Int) = ENERGY_COLORS[e.coerceIn(0, ENERGY_COLORS.lastIndex)]

    /** Le fond : un dégradé d'espace profond, deux nébuleuses et une poussière d'étoiles. */
    fun backdrop(canvas: Canvas, w: Float, h: Float, seed: Int, unit: Float, boss: Boolean = false) {
        fill.shader = LinearGradient(0f, 0f, 0f, h, SPACE_TOP, SPACE_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        val rnd = Random(seed)
        fun nebula(color: Int) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            val r = maxOf(w, h) * (0.35f + rnd.nextFloat() * 0.2f)
            fill.shader = RadialGradient(x, y, r, color, color and 0xFFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r, fill)
        }
        // L'étage du boss rougeoie : on sent que ce n'est plus une grille comme les autres.
        nebula(if (boss) 0x38A0184A else 0x243A1C8C)
        nebula(if (boss) 0x26C8401C else 0x1A0E6E8C)
        fill.shader = null
        val count = (w * h / (unit * unit * 0.8f)).toInt().coerceIn(30, 600)
        repeat(count) {
            fill.color = Color.argb(30 + rnd.nextInt(110), 200, 210, 255)
            canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, unit * (0.01f + rnd.nextFloat() * 0.018f), fill)
        }
    }

    /** Une case de la grille : un carré à peine visible, pour qu'on voie où poser. */
    fun cellFrame(canvas: Canvas, left: Float, top: Float, cell: Float) {
        fill.color = 0x0DFFFFFF
        val inset = cell * 0.06f
        canvas.drawRoundRect(left + inset, top + inset, left + cell - inset, top + cell - inset, cell * 0.14f, cell * 0.14f, fill)
    }

    /**
     * Un atome d'énergie [energy] en ([x], [y]). [time] en secondes fait tourner les
     * électrons ; [phase] décale chaque atome pour qu'ils ne tournent pas en chœur.
     * [boost] (0..1) l'éclaire davantage, pour la vague de victoire.
     */
    fun atom(canvas: Canvas, x: Float, y: Float, cell: Float, energy: Int, time: Float, phase: Float, boost: Float = 0f) {
        val color = energyColor(energy)
        if (energy == 0) {
            glow(canvas, x, y, cell * 0.3f, color, 0.35f + boost * 0.6f)
            fill.color = blend(color, Color.WHITE, boost * 0.7f)
            canvas.drawCircle(x, y, cell * (0.06f + boost * 0.04f), fill)
            return
        }
        glow(canvas, x, y, cell * (0.3f + 0.06f * energy), color, 0.28f + 0.08f * energy)
        for (k in 1..energy) {
            val r = cell * (0.13f + 0.075f * k)
            stroke.color = (color and 0xFFFFFF) or (0x8C shl 24)
            stroke.strokeWidth = cell * 0.026f
            canvas.drawCircle(x, y, r, stroke)
            // L'électron : les orbites intérieures tournent plus vite, en sens alternés.
            val speed = (2.2f - k * 0.35f) * (if (k % 2 == 0) -1f else 1f)
            val a = time * speed + phase + k * 1.7f
            fill.color = blend(color, Color.WHITE, 0.55f)
            canvas.drawCircle(x + cos(a) * r, y + sin(a) * r, cell * 0.034f, fill)
        }
        fill.color = blend(color, Color.WHITE, 0.6f)
        canvas.drawCircle(x, y, cell * 0.075f, fill)
    }

    /**
     * Le fil d'onde entre deux atomes intriqués : une sinusoïde qui ondule avec [time],
     * s'arrête au bord des deux atomes, et s'amortit vers ses bouts.
     */
    fun entanglement(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, cell: Float, color: Int, time: Float, alpha: Float = 1f) {
        val len = hypot(x2 - x1, y2 - y1)
        if (len < 1f) return
        val ux = (x2 - x1) / len; val uy = (y2 - y1) / len
        val skip = cell * 0.42f
        val usable = len - 2 * skip
        if (usable <= 0f) return
        val waves = (usable / (cell * 0.55f)).coerceAtLeast(1f)
        val amp = cell * 0.07f
        val steps = (usable / (cell * 0.08f)).toInt().coerceIn(8, 160)
        wavePath.reset()
        for (i in 0..steps) {
            val s = i / steps.toFloat()
            val envelope = sin(s * PI).toFloat()
            val off = sin(s * waves * 2 * PI - time * 3.0).toFloat() * amp * envelope
            val px = x1 + ux * (skip + usable * s) - uy * off
            val py = y1 + uy * (skip + usable * s) + ux * off
            if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
        }
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        stroke.color = color; stroke.alpha = a * 0x30 / 255; stroke.strokeWidth = cell * 0.1f
        canvas.drawPath(wavePath, stroke)
        stroke.color = color; stroke.alpha = a * 0xB0 / 255; stroke.strokeWidth = cell * 0.025f
        canvas.drawPath(wavePath, stroke)
        stroke.alpha = 255
    }

    /** Le liseré de couleur autour d'un atome intriqué, pour qu'on retrouve son jumeau. */
    fun pairRing(canvas: Canvas, x: Float, y: Float, cell: Float, color: Int, alpha: Float = 1f) {
        stroke.color = color
        stroke.alpha = (alpha.coerceIn(0f, 1f) * 0x90).toInt()
        stroke.strokeWidth = cell * 0.022f
        canvas.drawCircle(x, y, cell * 0.46f, stroke)
        stroke.alpha = 255
    }

    /** Un halo doux ; les dégradés sont gardés par couleur, à rayon 1. */
    fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        if (alpha <= 0f || radius <= 0f) return
        val shader = glowShaders.getOrPut(color) {
            RadialGradient(0f, 0f, 1f, intArrayOf(color, (color and 0xFFFFFF) or (0x55 shl 24), color and 0xFFFFFF),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        fill.shader = shader
        fill.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.save(); canvas.translate(x, y); canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, fill)
        canvas.restore()
        fill.shader = null
        fill.alpha = 255
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - k) + ((b shr s) and 0xFF) * k).toInt()
        return Color.argb(a ushr 24, ch(16), ch(8), ch(0))
    }
}
