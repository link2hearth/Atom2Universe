package com.Atom2Universe.app.games.cosmorun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
    val sfx = CosmoRunSfx(context)
    var resumeCountdown = 0f
        private set
    private var active = false
    private var lastNs = 0L
    private var clock = 0f
    private var hudTimer = 0f
    private var notified = false
    private var flash = 0f
    private var flashColor = Color.WHITE
    private var shake = 0f
    private var hitStop = 0f
    private var warden: Shader? = null
    private var wardenW = 0
    private var wardenH = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var color = 0
    }
    private val particles = Array(72) { Particle() }
    private var particleIndex = 0
    private var gestureDone = false
    private var pointerId = -1
    private val gesture = CosmoRunGesture(24f * resources.displayMetrics.density)

    init {
        isFocusableInTouchMode = true
        game.onEvent = { event, value ->
            when (event) {
                CosmoRunGame.Event.ATOM -> {
                    burst(0xFFFFCE7B.toInt(), 5)
                    sfx.play(CosmoSound.ATOM, .8f, CosmoRunSynth.LADDER[(value - 1).coerceAtLeast(0) % CosmoRunSynth.LADDER.size], 20L)
                }
                CosmoRunGame.Event.STUMBLE -> {
                    flash = .5f; flashColor = 0xFFFF527D.toInt(); burst(flashColor, 22)
                    shake = .32f; hitStop = .07f
                    sfx.play(CosmoSound.STUMBLE); sfx.play(CosmoSound.WARDEN, .5f)
                    if (feedbackEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                CosmoRunGame.Event.CRASH -> {
                    flash = .6f; flashColor = 0xFFFF527D.toInt(); burst(flashColor, 30)
                    shake = .45f; hitStop = .1f
                    sfx.play(CosmoSound.STUMBLE); sfx.play(CosmoSound.GAME_OVER, .8f)
                    if (feedbackEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                CosmoRunGame.Event.SHIELD_BREAK -> {
                    flash = .3f; flashColor = 0xFF54E5E0.toInt(); burst(flashColor, 24); shake = .18f; hitStop = .05f
                    sfx.play(CosmoSound.SHIELD_BREAK)
                    if (feedbackEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                CosmoRunGame.Event.SMASH -> { burst(0xFFFFB45E.toInt(), 10); sfx.play(CosmoSound.SMASH, .7f, cooldownMs = 40L) }
                CosmoRunGame.Event.SCRAPE -> { shake = .12f; sfx.play(CosmoSound.SMASH, .5f, .7f, 120L) }
                CosmoRunGame.Event.CLEAR -> sfx.play(CosmoSound.GRAZE, .6f, 1f, 60L)
                CosmoRunGame.Event.METEOR -> { shake = maxOf(shake, .08f); sfx.play(CosmoSound.LAND, .7f, .6f, 80L) }
                CosmoRunGame.Event.JUMP -> sfx.play(CosmoSound.JUMP, .7f, cooldownMs = 60L)
                CosmoRunGame.Event.LAND -> sfx.play(CosmoSound.LAND, if (value == 1) .8f else .45f, cooldownMs = 60L)
                CosmoRunGame.Event.SLIDE -> sfx.play(CosmoSound.SLIDE, .6f, cooldownMs = 120L)
                CosmoRunGame.Event.LANE -> sfx.play(CosmoSound.LANE, .5f, cooldownMs = 40L)
                CosmoRunGame.Event.MISSION -> {
                    burst(0xFF54E5E0.toInt(), 28); flash = .16f; flashColor = 0xFF54E5E0.toInt()
                    sfx.play(CosmoSound.MISSION)
                }
                CosmoRunGame.Event.RANK -> { burst(0xFFFFCE7B.toInt(), 40); sfx.play(CosmoSound.SECTOR, .9f, 1.3f) }
                CosmoRunGame.Event.RECORD -> {
                    burst(0xFFFFCE7B.toInt(), 36); flash = .2f; flashColor = 0xFFFFCE7B.toInt()
                    sfx.play(CosmoSound.MISSION, .9f, 1.15f)
                }
                CosmoRunGame.Event.SECTOR -> sfx.play(CosmoSound.SECTOR, .8f)
                CosmoRunGame.Event.TIER -> sfx.play(CosmoSound.ATOM, .9f, 2f, 0L)
                CosmoRunGame.Event.WARDEN_OFF -> Unit
                CosmoRunGame.Event.SHIELD -> { burst(0xFFBDA0FF.toInt(), 14); sfx.play(CosmoSound.SHIELD_GET) }
                CosmoRunGame.Event.MAGNET -> { burst(0xFFBDA0FF.toInt(), 14); sfx.play(CosmoSound.MAGNET) }
                CosmoRunGame.Event.BOOST -> { burst(0xFFBDA0FF.toInt(), 14); sfx.play(CosmoSound.OVERDRIVE) }
            }
            if (feedbackEnabled && (event == CosmoRunGame.Event.SHIELD || event == CosmoRunGame.Event.MAGNET ||
                    event == CosmoRunGame.Event.BOOST)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onEvent?.invoke(event)
        }
    }

    fun startGame() {
        notified = false; paused = false; flash = 0f; resumeCountdown = 0f; shake = 0f; hitStop = 0f
        particles.forEach { it.life = 0f }
        game.start(); lastNs = 0L
        requestFocus(); invalidate(); onHud?.invoke()
    }
    fun setPaused(value: Boolean) {
        if (paused && !value && game.isRunning) resumeCountdown = 3f
        paused = value; lastNs = 0L; pointerId = -1; gestureDone = true
        invalidate()
    }
    fun resume() { active = true; lastNs = 0L; sfx.start(); sfx.resume(); postInvalidateOnAnimation() }
    fun pause() { active = false; lastNs = 0L; pointerId = -1; sfx.pause() }
    override fun onDetachedFromWindow() { pause(); sfx.release(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastNs == 0L || !active) 0f else ((now - lastNs) / 1e9f).coerceIn(0f, .1f)
        lastNs = now
        if (!paused) {
            clock += dt
            if (hitStop > 0f) hitStop = (hitStop - dt).coerceAtLeast(0f)
            else if (resumeCountdown > 0f) resumeCountdown = (resumeCountdown - dt).coerceAtLeast(0f)
            else game.update(dt)
            for (p in particles) if (p.life > 0f) {
                p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt; p.vy += height * .3f * dt
            }
            flash = (flash - dt).coerceAtLeast(0f)
            shake = (shake - dt).coerceAtLeast(0f)
        }
        canvas.save()
        if (shake > 0f) {
            val amplitude = width * .012f * (shake / .3f).coerceAtMost(1.4f)
            canvas.translate(sin(clock * 91f) * amplitude, cos(clock * 77f) * amplitude * .7f)
        }
        renderer.draw(canvas, width, height, game, clock, !game.isRunning && !game.isGameOver)
        for (p in particles) if (p.life > 0f) {
            paint.color = p.color; paint.alpha = (255f * (p.life / .6f).coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(p.x, p.y, width * .005f * (p.life / .6f + .2f), paint)
        }
        canvas.restore()
        drawWarden(canvas)
        if (flash > 0f) {
            paint.color = flashColor; paint.alpha = (flash * 110f).toInt().coerceIn(0, 100)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        paint.alpha = 255
        sfx.setHum(game.isRunning && !paused && active, (game.speed - CosmoRunGame.MIN_SPEED) /
            (CosmoRunGame.MAX_SPEED * 1.35f - CosmoRunGame.MIN_SPEED))
        sfx.setMusic(game.isRunning && !paused && active, game.chain, dt)
        hudTimer += dt
        if (hudTimer >= .1f) { hudTimer = 0f; onHud?.invoke() }
        if (game.isGameOver && !notified) { notified = true; onGameOver?.invoke() }
        if (active && !paused && !game.isGameOver) postInvalidateOnAnimation()
    }

    /** Liseré rouge tant que le Gardien est sur les talons : il pulse, puis s'éteint avec lui. */
    private fun drawWarden(canvas: Canvas) {
        if (game.wardenTime <= 0f || !game.isRunning) return
        if (warden == null || wardenW != width || wardenH != height) {
            wardenW = width; wardenH = height
            warden = RadialGradient(width * .5f, height * .5f, kotlin.math.max(width, height) * .62f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0xFFFF2A55.toInt()), floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP)
        }
        val fade = (game.wardenTime / 1.2f).coerceIn(0f, 1f)
        val pulse = .75f + .25f * sin(clock * 9f)
        paint.shader = warden; paint.alpha = (150f * fade * pulse).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null; paint.alpha = 255
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
                pointerId = event.getPointerId(0); gestureDone = false
                gesture.down(event.x, event.y, event.eventTime)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0 && !gestureDone) {
                    // Les points groupés d'un même événement sont rejoués : aucun geste n'est manqué.
                    for (h in 0 until event.historySize) apply(gesture.move(
                        event.getHistoricalX(index, h), event.getHistoricalY(index, h), event.getHistoricalEventTime(h)))
                    apply(gesture.move(event.getX(index), event.getY(index), event.eventTime))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureDone && pointerId != -1) {
                    // Un coup de pouce court et net est un geste ; seul un doigt immobile est un tap.
                    val swipe = gesture.up(event.x, event.y)
                    if (swipe != null) apply(swipe) else if (gesture.isTap) performClick()
                }
                pointerId = -1; parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { pointerId = -1; gestureDone = true }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) {
                pointerId = -1; gestureDone = true
            }
        }
        return true
    }

    private fun apply(swipe: CosmoRunGesture.Swipe?) {
        when (swipe) {
            CosmoRunGesture.Swipe.LEFT -> game.moveLeft()
            CosmoRunGesture.Swipe.RIGHT -> game.moveRight()
            CosmoRunGesture.Swipe.UP -> game.jump()
            CosmoRunGesture.Swipe.DOWN -> game.slide()
            null -> Unit
        }
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
