package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class StarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Int)

    private data class ShootingStar(
        val x: Float,
        val y: Float,
        val angle: Float,
        val speed: Float,
        val tailLength: Float,
        var progress: Float = 0f
    ) {
        val dx = cos(angle)
        val dy = sin(angle)
    }

    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var stars: List<Star> = emptyList()
    private val shootingStars: MutableList<ShootingStar> = mutableListOf()

    private var lastFrameMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val spawnRunnable = Runnable { triggerSpawn() }

    private fun scheduleNextSpawn() {
        val delayMs = 10_000L + Random.nextLong(5_000L)
        handler.postDelayed(spawnRunnable, delayMs)
    }

    private fun triggerSpawn() {
        if (width > 0 && height > 0 && visibility == VISIBLE && shootingStars.size < 2) {
            spawnShootingStar(width.toFloat(), height.toFloat())
        }
        scheduleNextSpawn()
    }

    private fun buildStars(w: Int, h: Int) {
        val rng = Random.Default
        val list = mutableListOf<Star>()
        repeat(28) {
            list.add(Star(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                radius = rng.nextFloat() * 1.2f + 0.8f,
                alpha = rng.nextInt(80) + 160
            ))
        }
        repeat(10) {
            list.add(Star(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                radius = rng.nextFloat() * 1.5f + 2.5f,
                alpha = rng.nextInt(60) + 195
            ))
        }
        stars = list
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) buildStars(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        // Si la vue était masquée par un overlay, les étoiles ont pu s'accumuler sans progresser
        if (lastFrameMs != 0L && now - lastFrameMs > 2_000L) {
            shootingStars.clear()
        }
        val dt = if (lastFrameMs == 0L) 16f else (now - lastFrameMs).coerceIn(1, 100).toFloat()
        lastFrameMs = now

        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        for (star in stars) {
            starPaint.alpha = star.alpha
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
        }

        val pixelsPerMs = 0.65f
        val iter = shootingStars.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            val totalDist = s.tailLength * 3f
            s.progress += (pixelsPerMs * s.speed * dt) / totalDist
            if (s.progress >= 1f) { iter.remove(); continue }

            val headX = s.x + s.dx * s.progress * totalDist
            val headY = s.y + s.dy * s.progress * totalDist

            // Global fade envelope: ramp in 10%, full 10-65%, ramp out 35%
            val alpha = when {
                s.progress < 0.10f -> s.progress / 0.10f
                s.progress > 0.65f -> 1f - (s.progress - 0.65f) / 0.35f
                else -> 1f
            }.coerceIn(0f, 1f)

            // Trail: up to tailLength behind the head, gradient transparent→white
            val tailX = headX - s.dx * s.tailLength
            val tailY = headY - s.dy * s.tailLength

            trailPaint.strokeWidth = 3f
            trailPaint.shader = LinearGradient(
                tailX, tailY, headX, headY,
                intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            trailPaint.alpha = (alpha * 255).toInt()
            canvas.drawLine(tailX, tailY, headX, headY, trailPaint)

            // Head: shrinks from big (flash) to small as progress advances
            // Flash intensity strongest in first 20% of lifetime
            val flashFactor = (1f - s.progress / 0.20f).coerceIn(0f, 1f)

            // Glow halo during flash phase
            if (flashFactor > 0f) {
                val glowRadius = 18f * flashFactor + 4f
                glowPaint.shader = RadialGradient(
                    headX, headY, glowRadius,
                    intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
                glowPaint.alpha = (alpha * flashFactor * 200).toInt()
                canvas.drawCircle(headX, headY, glowRadius, glowPaint)
            }

            // Solid head dot: larger at start (meteor), shrinks over time
            val headRadius = 1.5f + 4f * (1f - s.progress).coerceIn(0f, 1f)
            starPaint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(headX, headY, headRadius, starPaint)
        }

        if (shootingStars.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun spawnShootingStar(w: Float, h: Float) {
        val baseAngle = if (Random.nextBoolean()) 30.0 else 150.0
        val angleRad = Math.toRadians(baseAngle + Random.nextDouble(-25.0, 25.0)).toFloat()
        val speed = Random.nextFloat() * 0.8f + 0.8f
        // Long tail: 150–320px
        val tail = Random.nextFloat() * 170f + 150f
        val startX = Random.nextFloat() * w
        val startY = Random.nextFloat() * h * 0.45f
        shootingStars.add(ShootingStar(startX, startY, angleRad, speed, tail))
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameMs = 0L
        handler.postDelayed(spawnRunnable, 3_000L)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(spawnRunnable)
        shootingStars.clear()
    }
}
