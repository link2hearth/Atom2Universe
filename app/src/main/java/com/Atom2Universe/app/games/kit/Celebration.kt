package com.Atom2Universe.app.games.kit

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.min

/**
 * La fête de victoire, commune aux puzzles et aux jeux de cartes : le plateau rebondit, une vague
 * arc-en-ciel le balaie, des confettis jaillissent du centre, un halo de la couleur du thème en fait
 * le tour, et le carillon joue ([KitSounds]).
 *
 * La vue qui la porte appelle [start] à la victoire, applique [scale] à son dessin (si elle veut le
 * rebond), puis [drawOver] par-dessus.
 */
class Celebration(private val view: View) {

    /** Temps écoulé, de 0 à 1 sur [DURATION_MS]. */
    private var t = 0f
    private var animator: ValueAnimator? = null

    private class Confetto(val x: Float, val y: Float, val vx: Float, val vy: Float, val angle: Float, val spin: Float,
                           val colour: Int, val size: Float)
    private val confetti = ArrayList<Confetto>()
    private val shimmer = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val density = view.resources.displayMetrics.density

    /** Vrai pendant que la fête se joue (le dessin en a besoin pour ne pas la rater). */
    val running: Boolean get() = t in 0.001f..0.999f

    fun start(palette: KitPalette) {
        animator?.cancel()
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        KitSounds.victory(view.context)
        if (!ValueAnimator.areAnimatorsEnabled()) return
        confetti.clear()
        val random = kotlin.random.Random(System.nanoTime())
        val size = min(view.width, view.height).toFloat()
        repeat(80) { k ->
            val angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI * 1.4
            val speed = size * (0.55f + random.nextFloat() * 0.9f)
            confetti.add(Confetto(view.width / 2f, view.height / 2f, (kotlin.math.cos(angle) * speed).toFloat(),
                (kotlin.math.sin(angle) * speed).toFloat(), random.nextFloat() * 360f, (random.nextFloat() - 0.5f) * 720f,
                palette.piece(k), size * (0.012f + random.nextFloat() * 0.014f)))
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = null
            addUpdateListener { t = it.animatedValue as Float; view.invalidate() }
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
    }

    /** Le rebond du plateau : il grossit un peu, revient, rebondit moins, et s'arrête. */
    val scale: Float
        get() {
            val b = t * DURATION_MS / 650f
            if (!running || b >= 1f) return 1f
            return 1f + 0.07f * kotlin.math.sin(b * Math.PI.toFloat() * 3f) * (1f - b)
        }

    /** La vague, le halo et les confettis, dessinés par-dessus le plateau [area]. */
    fun drawOver(canvas: Canvas, area: RectF, palette: KitPalette) {
        if (!running) return
        // La vague arc-en-ciel : une bande en diagonale qui traverse le plateau.
        val sweep = (t * DURATION_MS / 1100f)
        if (sweep < 1f) {
            val span = area.width() + area.height()
            val pos = area.left - area.height() + sweep * (span + area.height())
            val band = area.height() * 0.9f
            val colours = IntArray(7) { palette.withAlpha(palette.piece(it), 0.38f * (1f - sweep * 0.5f)) }
            shimmer.shader = LinearGradient(pos - band, area.top, pos + band, area.bottom,
                intArrayOf(0) + colours + intArrayOf(0), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(area, 12f * density, 12f * density, shimmer)
            shimmer.shader = null
        }
        // Le halo de la couleur du thème.
        val halo = (t * DURATION_MS / 1400f).coerceAtMost(1f)
        if (halo < 1f) {
            ring.color = palette.withAlpha(palette.accent, (1f - halo) * 0.85f)
            ring.strokeWidth = 3f * density + 10f * density * halo
            val inset = -6f * density * halo
            canvas.drawRoundRect(area.left - inset, area.top - inset, area.right + inset, area.bottom + inset,
                12f * density, 12f * density, ring)
        }
        // Les confettis : lancés du centre, ils retombent en tournant et s'effacent.
        val secs = t * DURATION_MS / 1000f
        val gravity = min(view.width, view.height) * 1.6f
        val fade = (1f - t).coerceIn(0f, 1f)
        for (c in confetti) {
            val x = c.x + c.vx * secs
            val y = c.y + c.vy * secs + 0.5f * gravity * secs * secs
            if (y > view.height + 50) continue
            fill.color = palette.withAlpha(c.colour, fade)
            canvas.save()
            canvas.rotate(c.angle + c.spin * secs, x, y)
            canvas.drawRect(x - c.size, y - c.size * 0.5f, x + c.size, y + c.size * 0.5f, fill)
            canvas.restore()
        }
    }

    companion object {
        const val DURATION_MS = 2200L
    }
}
