package com.Atom2Universe.app.games.orbite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rendu temps réel du jeu [OrbiteGame] dans une [SurfaceView] avec un thread de
 * rendu unique (créé dans [surfaceCreated], arrêté dans [surfaceDestroyed]).
 *
 * La vue ajoute le « jus » visuel : champ d'étoiles, lueur du noyau, traînée de
 * l'électron, particules de capture / d'absorption / d'explosion.
 */
class OrbiteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    val game = OrbiteGame()

    @Volatile private var running = false
    @Volatile private var pendingStart = false
    private var thread: Thread? = null

    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver: (() -> Unit)? = null
    private var lastScore = -1
    private var gameOverFired = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Couleurs (palette cosmique) ─────────────────────────────────────────
    private val colBg = Color.parseColor("#060914")
    private val colNucleus = Color.parseColor("#FFB347")
    private val colNucleusCore = Color.parseColor("#FF6B35")
    private val colElectron = Color.parseColor("#36E1FF")
    private val colOrbit = Color.parseColor("#1E3A5F")
    private val colAsteroid = Color.parseColor("#FF5A6E")
    private val colQuantum = Color.parseColor("#7CFFCB")

    // ── Champ d'étoiles (statique) ──────────────────────────────────────────
    private class Star(val x: Float, val y: Float, val r: Float, val a: Int)
    private var stars = emptyArray<Star>()

    // ── Traînée de l'électron ───────────────────────────────────────────────
    private val trailX = FloatArray(TRAIL_LEN)
    private val trailY = FloatArray(TRAIL_LEN)
    private var trailHead = 0
    private var trailCount = 0

    // ── Particules ──────────────────────────────────────────────────────────
    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )
    private val particles = ArrayList<Particle>()

    private var flashAlpha = 0f
    private var nucleusPulse = 0f

    init { holder.addCallback(this) }

    fun startGame() {
        pendingStart = true
    }

    // ── Cycle de vie de la surface ──────────────────────────────────────────
    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "orbite").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        game.configure(w, h)
        buildStars(w, h)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join(2000)
        thread = null
    }

    private fun buildStars(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val n = 110
        stars = Array(n) {
            Star(
                Random.nextFloat() * w,
                Random.nextFloat() * h,
                Random.nextFloat() * 1.8f + 0.5f,
                Random.nextInt(40, 170)
            )
        }
    }

    // ── Boucle de rendu ─────────────────────────────────────────────────────
    override fun run() {
        var lastNanos = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dtMs = ((now - lastNanos) / 1_000_000L).coerceAtMost(50L)
            lastNanos = now

            if (pendingStart) {
                pendingStart = false
                gameOverFired = false
                lastScore = -1
                particles.clear()
                trailCount = 0; trailHead = 0
                flashAlpha = 0f
                wireCallbacks()
                game.start()
            }

            if (game.isRunning) {
                game.update(dtMs)
                pushTrail()
            }
            stepEffects(dtMs)

            if (game.score != lastScore) {
                lastScore = game.score
                onScoreChanged?.invoke(game.score)
            }
            if (game.isGameOver && !gameOverFired) {
                gameOverFired = true
                onGameOver?.invoke()
            }

            val canvas = holder.lockCanvas()
            if (canvas != null) try { drawGame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }

            val frameMs = (System.nanoTime() - now) / 1_000_000L
            if (frameMs < 16L) try { Thread.sleep(16L - frameMs) } catch (_: InterruptedException) {}
        }
    }

    private fun wireCallbacks() {
        game.onCollect = { x, y, pts -> burst(x, y, colQuantum, 14, 2.4f); if (pts >= 6) flashAlpha = 0.18f }
        game.onAbsorb = { x, y -> burst(x, y, colNucleusCore, 8, 1.6f) }
        game.onDeath = { x, y -> burst(x, y, colElectron, 40, 4.2f); burst(x, y, Color.WHITE, 18, 3.0f); flashAlpha = 0.9f }
    }

    private fun pushTrail() {
        trailX[trailHead] = game.electronX()
        trailY[trailHead] = game.electronY()
        trailHead = (trailHead + 1) % TRAIL_LEN
        if (trailCount < TRAIL_LEN) trailCount++
    }

    private fun stepEffects(dtMs: Long) {
        val dt = dtMs / 1000f
        nucleusPulse += dt
        if (flashAlpha > 0f) flashAlpha = (flashAlpha - dt * 2.2f).coerceAtLeast(0f)
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.93f
            p.vy *= 0.93f
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int, speedScale: Float) {
        val base = game.electronR * 6f * speedScale
        repeat(count) {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val sp = (Random.nextFloat() * 0.7f + 0.3f) * base
            val life = Random.nextFloat() * 0.5f + 0.35f
            particles.add(
                Particle(
                    x, y,
                    cos(ang) * sp, sin(ang) * sp,
                    life, life, color,
                    game.electronR * (Random.nextFloat() * 0.4f + 0.35f)
                )
            )
        }
    }

    // ── Entrée ──────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            game.reverse()
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    // ── Dessin ──────────────────────────────────────────────────────────────
    private fun drawGame(canvas: Canvas) {
        canvas.drawColor(colBg)
        drawStars(canvas)

        if (game.orbitR <= 0f) return
        val cx = game.cx; val cy = game.cy

        // Anneau orbital.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = game.minOrbitStroke()
        paint.color = colOrbit
        canvas.drawCircle(cx, cy, game.orbitR, paint)
        paint.style = Paint.Style.FILL

        drawQuanta(canvas)
        drawAsteroids(canvas)
        drawNucleus(canvas, cx, cy)
        if (game.isRunning || game.isGameOver) drawTrailAndElectron(canvas)
        drawParticles(canvas)

        if (flashAlpha > 0f) {
            canvas.drawColor(Color.argb((flashAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255))
        }
    }

    private fun drawStars(canvas: Canvas) {
        for (s in stars) {
            paint.color = Color.argb(s.a, 200, 220, 255)
            canvas.drawCircle(s.x, s.y, s.r, paint)
        }
    }

    private fun drawNucleus(canvas: Canvas, cx: Float, cy: Float) {
        val pulse = 1f + 0.06f * sin(nucleusPulse * 3f)
        val r = game.nucleusR * pulse
        // Halo.
        glowPaint.shader = RadialGradient(
            cx, cy, r * 2.6f,
            intArrayOf(Color.argb(150, 255, 160, 70), Color.argb(0, 255, 120, 50)),
            floatArrayOf(0.35f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 2.6f, glowPaint)
        glowPaint.shader = null
        // Cœur.
        paint.color = colNucleus
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = colNucleusCore
        canvas.drawCircle(cx, cy, r * 0.62f, paint)
        // Quelques nucléons qui frémissent.
        paint.color = Color.argb(200, 255, 230, 200)
        for (i in 0 until 5) {
            val a = nucleusPulse * 0.8f + i * 1.25f
            val rr = r * 0.45f
            canvas.drawCircle(
                cx + cos(a) * rr, cy + sin(a) * rr,
                r * 0.14f, paint
            )
        }
    }

    private fun drawAsteroids(canvas: Canvas) {
        for (a in game.asteroids) {
            val ax = game.cx + cos(a.angle) * a.dist
            val ay = game.cy + sin(a.angle) * a.dist
            // Traînée vers l'extérieur.
            val tx = game.cx + cos(a.angle) * (a.dist + a.radius * 4f)
            val ty = game.cy + sin(a.angle) * (a.dist + a.radius * 4f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = a.radius * 0.9f
            paint.color = Color.argb(80, 255, 90, 110)
            canvas.drawLine(ax, ay, tx, ty, paint)
            paint.style = Paint.Style.FILL
            // Halo.
            glowPaint.shader = RadialGradient(
                ax, ay, a.radius * 2.2f,
                intArrayOf(Color.argb(120, 255, 90, 110), Color.argb(0, 255, 90, 110)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(ax, ay, a.radius * 2.2f, glowPaint)
            glowPaint.shader = null
            // Caillou irrégulier (cercle + bosse pseudo-aléatoire stable via seed).
            paint.color = colAsteroid
            canvas.drawCircle(ax, ay, a.radius, paint)
            paint.color = Color.argb(160, 120, 20, 35)
            val ox = ((a.seed % 7) - 3) / 6f * a.radius
            val oy = (((a.seed / 7) % 7) - 3) / 6f * a.radius
            canvas.drawCircle(ax + ox, ay + oy, a.radius * 0.35f, paint)
        }
    }

    private fun drawQuanta(canvas: Canvas) {
        for (q in game.quanta) {
            val t = q.ageMs / q.lifeMs.toFloat()
            val alpha = when {
                t < 0.12f -> t / 0.12f               // fondu d'entrée
                t > 0.75f -> (1f - t) / 0.25f         // fondu de sortie
                else -> 1f
            }.coerceIn(0f, 1f)
            val qx = game.cx + cos(q.angle) * game.orbitR
            val qy = game.cy + sin(q.angle) * game.orbitR
            val pulse = 1f + 0.25f * sin(q.ageMs / 120f)
            val r = game.electronR * 0.8f * pulse
            glowPaint.shader = RadialGradient(
                qx, qy, r * 2.6f,
                intArrayOf(
                    Color.argb((alpha * 160).toInt(), 124, 255, 203),
                    Color.argb(0, 124, 255, 203)
                ),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(qx, qy, r * 2.6f, glowPaint)
            glowPaint.shader = null
            paint.color = Color.argb((alpha * 255).toInt(), Color.red(colQuantum), Color.green(colQuantum), Color.blue(colQuantum))
            // Petit losange (quantum d'énergie).
            canvas.save()
            canvas.rotate(45f, qx, qy)
            canvas.drawRect(qx - r, qy - r, qx + r, qy + r, paint)
            canvas.restore()
        }
    }

    private fun drawTrailAndElectron(canvas: Canvas) {
        // Traînée.
        for (i in 0 until trailCount) {
            val idx = ((trailHead - 1 - i) % TRAIL_LEN + TRAIL_LEN) % TRAIL_LEN
            val frac = 1f - i / TRAIL_LEN.toFloat()
            paint.color = Color.argb((frac * 90).toInt(), 54, 225, 255)
            canvas.drawCircle(trailX[idx], trailY[idx], game.electronR * frac * 0.8f, paint)
        }
        val ex = game.electronX(); val ey = game.electronY()
        // Halo.
        glowPaint.shader = RadialGradient(
            ex, ey, game.electronR * 2.6f,
            intArrayOf(Color.argb(160, 54, 225, 255), Color.argb(0, 54, 225, 255)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(ex, ey, game.electronR * 2.6f, glowPaint)
        glowPaint.shader = null
        paint.color = colElectron
        canvas.drawCircle(ex, ey, game.electronR, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(ex, ey, game.electronR * 0.45f, paint)
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val frac = (p.life / p.maxLife).coerceIn(0f, 1f)
            paint.color = Color.argb((frac * 255).toInt(), Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            canvas.drawCircle(p.x, p.y, p.size * frac, paint)
        }
    }

    private fun OrbiteGame.minOrbitStroke() = (minOf(width, height) * 0.006f).coerceAtLeast(2f)

    companion object {
        private const val TRAIL_LEN = 16
    }
}
