package com.Atom2Universe.app.crypto.fusion

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt
import kotlin.random.Random

class FusionConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val color: Int,
        val size: Float,
        var rotation: Float,
        val rotSpeed: Float,
        var alpha: Float = 1f
    )

    private data class Peg(val x: Float, val y: Float)

    private val particles = mutableListOf<Particle>()
    private val pegs = mutableListOf<Peg>()
    private var pegCollisionRadius = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private var lastFrameMs = 0L

    private val colors = intArrayOf(
        0xFFFF3366.toInt(), 0xFFFF8800.toInt(), 0xFFFFCC00.toInt(),
        0xFF44FF66.toInt(), 0xFF00CCFF.toInt(), 0xFF9933FF.toInt(),
        0xFFFF33CC.toInt(), 0xFFFFFFFF.toInt()
    )

    fun launch() {
        particles.clear()
        pegs.clear()
        if (width == 0 || height == 0) {
            post { buildAndLaunch() }
        } else {
            buildAndLaunch()
        }
    }

    private fun buildAndLaunch() {
        buildPegs()
        spawnParticles()
        lastFrameMs = System.currentTimeMillis()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE
            addUpdateListener {
                val now = System.currentTimeMillis()
                val dt = ((now - lastFrameMs) / 1000f).coerceAtMost(0.05f)
                lastFrameMs = now
                updateParticles(dt)
                invalidate()
            }
            start()
        }
    }

    private fun buildPegs() {
        val cols = 7
        val rows = 8
        val spacingX = width / (cols + 1).toFloat()
        val spacingY = height * 0.55f / rows
        // Rayon de collision = 18% de l'espacement : les billes passent librement entre picots
        pegCollisionRadius = spacingX * 0.18f

        for (row in 0 until rows) {
            val isOdd = row % 2 != 0
            val colCount = if (isOdd) cols - 1 else cols
            val offsetX = if (isOdd) spacingX * 1.5f else spacingX
            for (col in 0 until colCount) {
                pegs.add(Peg(
                    x = offsetX + col * spacingX,
                    y = height * 0.08f + spacingY * row
                ))
            }
        }
    }

    private fun spawnParticles() {
        val cx = width / 2f
        repeat(90) {
            particles += Particle(
                x = cx + (Random.nextFloat() - 0.5f) * width * 0.30f,
                y = -Random.nextFloat() * height * 0.05f,
                vx = (Random.nextFloat() - 0.5f) * 3f,
                vy = Random.nextFloat() * 2f + 1f,
                color = colors[Random.nextInt(colors.size)],
                size = Random.nextFloat() * 9f + 5f,
                rotation = Random.nextFloat() * 360f,
                rotSpeed = (Random.nextDouble(-5.0, 5.0)).toFloat()
            )
        }
    }

    private fun updateParticles(dt: Float) {
        val gravity = 280f
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()

            p.vy += gravity * dt
            p.x += p.vx * dt * 60f
            p.y += p.vy * dt * 60f
            p.rotation += p.rotSpeed * dt * 60f

            // Rebond sur les picots
            for (peg in pegs) {
                val dx = p.x - peg.x
                val dy = p.y - peg.y
                val dist = sqrt(dx * dx + dy * dy)
                val minDist = pegCollisionRadius + p.size * 0.4f
                if (dist < minDist && dist > 0.1f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val dot = p.vx * nx + p.vy * ny
                    if (dot < 0f) {
                        p.vx -= 2f * dot * nx
                        p.vy -= 2f * dot * ny
                        // Amortissement + légère perturbation aléatoire
                        p.vx = p.vx * 0.75f + (Random.nextFloat() - 0.5f) * 1.5f
                        p.vy = p.vy * 0.75f
                        // Dégager la bille du picot
                        val overlap = minDist - dist
                        p.x += nx * overlap
                        p.y += ny * overlap
                    }
                }
            }

            // Fade et suppression en bas de l'écran
            if (p.y > height * 0.85f) {
                p.alpha = (p.alpha - dt * 0.6f).coerceAtLeast(0f)
                if (p.alpha <= 0f) iter.remove()
            }
        }
        if (particles.isEmpty()) animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt()
            canvas.save()
            canvas.rotate(p.rotation, p.x, p.y)
            val half = p.size / 2f
            canvas.drawRect(p.x - half, p.y - half, p.x + half, p.y + half, paint)
            canvas.restore()
        }
    }

    fun stop() {
        animator?.cancel()
        particles.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}
