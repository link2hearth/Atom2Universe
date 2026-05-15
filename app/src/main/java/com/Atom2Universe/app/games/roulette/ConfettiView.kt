package com.Atom2Universe.app.games.roulette

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class ConfettiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val particles = mutableListOf<Particle>()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler     = Handler(Looper.getMainLooper())

    private val confettiColors = intArrayOf(
        Color.parseColor("#FFD700"), Color.parseColor("#00E5FF"),
        Color.parseColor("#FF5252"), Color.parseColor("#B388FF"),
        Color.parseColor("#69FF47"), Color.parseColor("#FF9800"),
        Color.parseColor("#FF4081"), Color.parseColor("#80DEEA"),
        Color.parseColor("#FFFFFF"), Color.parseColor("#FFEB3B")
    )
    private val burstColors = intArrayOf(
        Color.parseColor("#FFD700"), Color.parseColor("#FFFFFF"),
        Color.parseColor("#FF9800"), Color.parseColor("#FF5252"),
        Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"),
        Color.parseColor("#FFEB3B"), Color.parseColor("#69FF47")
    )

    // ── Types de particules ───────────────────────────────────────────────

    private abstract inner class Particle {
        abstract var x: Float; abstract var y: Float; abstract var alpha: Float
        abstract fun update(); abstract fun draw(canvas: Canvas); abstract fun isDead(): Boolean
    }

    /** Confetti qui tombe depuis le haut */
    private inner class Confetti(
        override var x: Float, override var y: Float,
        private var vx: Float, private var vy: Float,
        private var rotation: Float, private val rotSpeed: Float,
        private val color: Int, private val size: Float, private val isRect: Boolean
    ) : Particle() {
        override var alpha = 1f
        override fun update() {
            x += vx; y += vy; vy += 0.22f; vx *= 0.995f; rotation += rotSpeed
            val h = height.takeIf { it > 0 } ?: 1
            if (y > h * 0.7f) alpha = ((h - y) / (h * 0.3f)).coerceIn(0f, 1f)
        }
        override fun draw(canvas: Canvas) {
            fillPaint.color = color; fillPaint.alpha = (alpha * 255).toInt()
            canvas.save(); canvas.translate(x, y); canvas.rotate(rotation)
            if (isRect) canvas.drawRect(-size / 2, -size / 4, size / 2, size / 4, fillPaint)
            else canvas.drawCircle(0f, 0f, size / 2, fillPaint)
            canvas.restore()
        }
        override fun isDead() = y > (height + 60).coerceAtLeast(200)
    }

    /** Onde de choc circulaire qui s'étend */
    private inner class Shockwave(
        override var x: Float, override var y: Float,
        private val color: Int, private val maxRadius: Float
    ) : Particle() {
        override var alpha = 1f
        private var radius = 20f
        override fun update() {
            radius += maxRadius / 22f
            alpha = (1f - radius / maxRadius).coerceIn(0f, 1f)
        }
        override fun draw(canvas: Canvas) {
            strokePaint.color = color
            strokePaint.strokeWidth = 14f * alpha + 4f
            strokePaint.alpha = (alpha * 230).toInt()
            canvas.drawCircle(x, y, radius, strokePaint)
        }
        override fun isDead() = radius >= maxRadius
    }

    /** Rayon lumineux qui s'élance vers l'extérieur */
    private inner class BurstRay(
        override var x: Float, override var y: Float,
        private val angle: Float, private val color: Int, private val length: Float
    ) : Particle() {
        override var alpha = 1f
        private var progress = 0f
        private val ca = cos(angle); private val sa = sin(angle)
        override fun update() { progress += 0.045f; alpha = (1f - progress).coerceIn(0f, 1f) }
        override fun draw(canvas: Canvas) {
            val r0 = length * (progress - 0.18f).coerceAtLeast(0f)
            val r1 = length * progress
            strokePaint.color = color
            strokePaint.strokeWidth = (16f * alpha + 4f)
            strokePaint.alpha = (alpha * 255).toInt()
            strokePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine((x + ca * r0).toFloat(), (y + sa * r0).toFloat(),
                            (x + ca * r1).toFloat(), (y + sa * r1).toFloat(), strokePaint)
        }
        override fun isDead() = alpha <= 0f
    }

    /** Grande étincelle qui vole et s'estompe lentement */
    private inner class Spark(
        override var x: Float, override var y: Float,
        private var vx: Float, private var vy: Float,
        private val color: Int, private val baseSize: Float
    ) : Particle() {
        override var alpha = 1f
        private var life = 1f
        override fun update() { x += vx; y += vy; vy += 0.2f; vx *= 0.97f; life -= 0.016f; alpha = life.coerceIn(0f, 1f) }
        override fun draw(canvas: Canvas) {
            fillPaint.color = color; fillPaint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(x, y, baseSize * life, fillPaint)
        }
        override fun isDead() = life <= 0f
    }

    // ── API publique ──────────────────────────────────────────────────────

    fun launch(multiplier: Int, winCount: Int) {
        handler.removeCallbacks(ticker)
        particles.clear()
        val w = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        // Pluie de confettis
        val confettiCount = when {
            multiplier <= 2 -> 55
            multiplier == 3 -> 110
            multiplier == 4 -> 170
            else            -> 230
        }
        repeat(confettiCount) { spawnConfetti(w) }

        // Bursts : au moins 1 dès que winCount >= 1 (multiplicateur élevé = burst plus gros)
        val burstCount = winCount.coerceIn(1, 5)
        val positions  = burstPositions(burstCount, w, h)
        positions.forEachIndexed { i, (bx, by) ->
            handler.postDelayed({
                fireBurst(bx, by, multiplier, w)
                invalidate()
            }, i * 220L)
        }

        handler.post(ticker)
    }

    private fun fireBurst(cx: Float, cy: Float, multiplier: Int, viewWidth: Int) {
        val maxR = viewWidth * 0.55f

        // Onde de choc (1 ou 2 selon multiplicateur)
        particles += Shockwave(cx, cy, pickBurstColor(), maxR)
        if (multiplier >= 4) particles += Shockwave(cx, cy, pickBurstColor(), maxR * 0.65f)

        // Rayons (8 à 16 selon multiplicateur)
        val rayCount = when {
            multiplier <= 2 -> 8
            multiplier == 3 -> 10
            multiplier == 4 -> 14
            else            -> 16
        }
        val rayLength = viewWidth * 0.42f
        repeat(rayCount) { i ->
            val angle = (i * 2 * Math.PI / rayCount + rnd() * 0.3).toFloat()
            particles += BurstRay(cx, cy, angle, pickBurstColor(), rayLength * (0.7f + rnd() * 0.5f))
        }

        // Grandes étincelles
        val sparkCount = 30 + multiplier * 12
        repeat(sparkCount) { spawnBurst(cx, cy) }
    }

    private fun burstPositions(count: Int, w: Int, h: Int): List<Pair<Float, Float>> {
        val cy = h * 0.4f
        return when (count) {
            1    -> listOf(Pair(w * 0.5f, cy))
            2    -> listOf(Pair(w * 0.28f, cy), Pair(w * 0.72f, cy))
            3    -> listOf(Pair(w * 0.5f, cy * 0.75f), Pair(w * 0.18f, cy * 1.15f), Pair(w * 0.82f, cy * 1.15f))
            4    -> listOf(Pair(w * 0.5f, cy * 0.7f), Pair(w * 0.15f, cy), Pair(w * 0.85f, cy), Pair(w * 0.5f, cy * 1.3f))
            else -> listOf(Pair(w * 0.5f, cy * 0.65f), Pair(w * 0.12f, cy * 0.9f), Pair(w * 0.88f, cy * 0.9f),
                           Pair(w * 0.25f, cy * 1.3f), Pair(w * 0.75f, cy * 1.3f))
        }
    }

    // ── Spawn helpers ─────────────────────────────────────────────────────

    private fun spawnConfetti(viewWidth: Int) {
        val size = 9f + rnd() * 18f
        particles += Confetti(
            x = rnd() * viewWidth,
            y = -20f - rnd() * 450f,
            vx = (rnd() - 0.5f) * 6f,
            vy = 2f + rnd() * 7f,
            rotation = rnd() * 360f,
            rotSpeed = (rnd() - 0.5f) * 14f,
            color = confettiColors[(rnd() * confettiColors.size).toInt()],
            size = size,
            isRect = rnd() > 0.45f
        )
    }

    private fun spawnBurst(cx: Float, cy: Float) {
        val angle = rnd() * 2 * Math.PI.toFloat()
        val speed = 8f + rnd() * 18f
        particles += Spark(
            x = cx, y = cy,
            vx = (cos(angle) * speed).toFloat(),
            vy = (sin(angle) * speed - 5f).toFloat(),
            color = pickBurstColor(),
            baseSize = 10f + rnd() * 16f
        )
    }

    private fun pickBurstColor() = burstColors[(rnd() * burstColors.size).toInt()]

    // ── Boucle d'animation ────────────────────────────────────────────────

    private val ticker = object : Runnable {
        override fun run() {
            val it = particles.iterator()
            while (it.hasNext()) { val p = it.next(); p.update(); if (p.isDead()) it.remove() }
            invalidate()
            if (particles.isNotEmpty()) handler.postDelayed(this, 16L)
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (p in particles) p.draw(canvas)
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        particles.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    private fun rnd() = Math.random().toFloat()
}
