package com.Atom2Universe.app.games.cosmorun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Vue accélérée matériellement et synchronisée sur les images Android, sans thread de rendu concurrent. */
class CosmoRunView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    val game = CosmoRunGame()
    val renderer = CosmoRunRenderer()
    var onGameOver: (() -> Unit)? = null
    var onHud: (() -> Unit)? = null
    var onEvent: ((CosmoRunGame.Event) -> Unit)? = null
    var paused = false
        private set
    var feedbackEnabled = true
    var resumeCountdown = 0f
        private set
    private var active = false
    private var lastNs = 0L
    private var clock = 0f
    private var hudTimer = 0f
    private var notified = false
    private var flash = 0f
    private var flashColor = Color.WHITE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var color = 0
    }
    private val particles = Array(72) { Particle() }
    private var particleIndex = 0
    private var downX = 0f
    private var downY = 0f
    private var gestureDone = false
    private var pointerId = -1
    private val threshold = 28f * resources.displayMetrics.density

    init {
        isFocusableInTouchMode = true
        game.onEvent = { event, _ ->
            when (event) {
                CosmoRunGame.Event.ATOM -> burst(0xFFFFCE7B.toInt(), 5)
                CosmoRunGame.Event.SHIELD_BREAK, CosmoRunGame.Event.CRASH -> {
                    flash = if (event == CosmoRunGame.Event.CRASH) .6f else .3f
                    flashColor = 0xFFFF527D.toInt(); burst(flashColor, 24)
                    if (feedbackEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                CosmoRunGame.Event.CONTRACT -> { burst(0xFF54E5E0.toInt(), 28); flash = .16f; flashColor = 0xFF54E5E0.toInt() }
                CosmoRunGame.Event.SECTOR -> Unit
                else -> {
                    burst(0xFFBDA0FF.toInt(), 14)
                    if (feedbackEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            onEvent?.invoke(event)
        }
    }

    fun startGame() {
        notified = false; paused = false; flash = 0f; resumeCountdown = 0f
        particles.forEach { it.life = 0f }
        game.start(); lastNs = 0L
        requestFocus(); invalidate(); onHud?.invoke()
    }
    fun setPaused(value: Boolean) {
        if (paused && !value && game.isRunning) resumeCountdown = 3f
        paused = value; lastNs = 0L; pointerId = -1; gestureDone = true
        invalidate()
    }
    fun resume() { active = true; lastNs = 0L; postInvalidateOnAnimation() }
    fun pause() { active = false; lastNs = 0L; pointerId = -1 }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastNs == 0L || !active) 0f else ((now - lastNs) / 1e9f).coerceIn(0f, .1f)
        lastNs = now
        if (!paused) {
            clock += dt
            if (resumeCountdown > 0f) resumeCountdown = (resumeCountdown - dt).coerceAtLeast(0f)
            else game.update(dt)
            for (p in particles) if (p.life > 0f) {
                p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt; p.vy += height * .3f * dt
            }
            flash = (flash - dt).coerceAtLeast(0f)
        }
        renderer.draw(canvas, width, height, game, clock, !game.isRunning && !game.isGameOver)
        for (p in particles) if (p.life > 0f) {
            paint.color = p.color; paint.alpha = (255f * (p.life / .6f).coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(p.x, p.y, width * .005f * (p.life / .6f + .2f), paint)
        }
        if (flash > 0f) {
            paint.color = flashColor; paint.alpha = (flash * 110f).toInt().coerceIn(0, 100)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        paint.alpha = 255
        hudTimer += dt
        if (hudTimer >= .1f) { hudTimer = 0f; onHud?.invoke() }
        if (game.isGameOver && !notified) { notified = true; onGameOver?.invoke() }
        if (active && !paused && !game.isGameOver) postInvalidateOnAnimation()
    }

    private fun burst(color: Int, count: Int) {
        if (width == 0) return
        val x = renderer.playerScreenX(game); val y = renderer.playerScreenY(game)
        repeat(count) { i ->
            val p = particles[particleIndex++ % particles.size]
            val a = i * 2.39996f + clock
            val speed = width * (.12f + (i % 5) * .045f)
            p.x = x; p.y = y; p.vx = cos(a) * speed; p.vy = sin(a) * speed - height * .07f
            p.life = .6f; p.color = color
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!game.isRunning || paused) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0); downX = event.x; downY = event.y; gestureDone = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0 && !gestureDone) {
                    val dx = event.getX(index) - downX; val dy = event.getY(index) - downY
                    if (maxOf(abs(dx), abs(dy)) >= threshold) {
                        if (abs(dx) > abs(dy)) { if (dx > 0) game.moveRight() else game.moveLeft() }
                        else if (dy < 0) game.jump() else game.slide()
                        gestureDone = true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureDone && pointerId != -1) performClick()
                pointerId = -1; parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { pointerId = -1; gestureDone = true }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) {
                pointerId = -1; gestureDone = true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (!paused) game.jump()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!game.isRunning || paused) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> game.moveLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT -> game.moveRight()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SPACE -> game.jump()
            KeyEvent.KEYCODE_DPAD_DOWN -> game.slide()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
