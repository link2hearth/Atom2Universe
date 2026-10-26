package com.Atom2Universe.app.games.mochi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

/** Simulation et saisie sur le fil UI ; le Canvas de View est accéléré matériellement. */
class MochiView(context: Context) : View(context) {
    var game = MochiGame(); private set
    var onChanged: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onRest: (() -> Unit)? = null
    private val art = MochiArt(context)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTypeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    private var active = false
    private var decorating = false
    private var visualTime = 0f
    private var currentBiome = 0
    private var previousBiome = 0
    private var biomeBlend = 1f
    private var viewportTop = 0f
    private var viewportBottom = 0f
    private var lastFrame = 0L
    private var pointer = -1
    private var downX = 0f
    private var downY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var pop = 0f
    private var lastHud = emptyList<Int>()
    private var cameraBottom = 0f
    private var impact = 0f
    private var finishDelay = -1f
    private data class Popup(val x: Float, val y: Float, val text: String, var life: Float = 1f)
    private val popups = ArrayList<Popup>()
    private val trail = ArrayDeque<MochiGame.Point>()
    private data class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val color: Int)
    private val particles = ArrayList<Particle>()
    private data class Burst(val x: Float, val y: Float, val blue: Boolean, var age: Float = 0f)
    private val bursts = ArrayList<Burst>()
    private val availableHeight get() = (height - viewportTop - viewportBottom).coerceAtLeast(1f)
    private val gaugeSpace get() = 48f * resources.displayMetrics.density
    private val scale get() = min((width - gaugeSpace) / MochiGame.WIDTH, availableHeight / MochiGame.HEIGHT).coerceAtLeast(.001f)
    private val left get() = (width - gaugeSpace - MochiGame.WIDTH * scale) / 2
    private val top get() = viewportTop + (availableHeight - MochiGame.HEIGHT * scale) / 2

    fun setViewportInsets(top: Int, bottom: Int) {
        if (viewportTop == top.toFloat() && viewportBottom == bottom.toFloat()) return
        viewportTop = top.toFloat(); viewportBottom = bottom.toFloat()
        cancelAim(); invalidate()
    }

    init {
        isClickable = true
        contentDescription = context.getString(R.string.mochi_touch_description)
    }

    private val frame = object : Runnable {
        override fun run() {
            if ((!active && !decorating) || !isAttachedToWindow) return
            val now = SystemClock.elapsedRealtimeNanos()
            val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, .05f)
            lastFrame = now
            visualTime += dt
            if (active) game.update(dt)
            if (currentBiome != game.biome) {
                previousBiome = currentBiome; currentBiome = game.biome; biomeBlend = 0f
            }
            biomeBlend = (biomeBlend + dt * .7f).coerceAtMost(1f)
            if (pointer != -1 && game.phase != MochiGame.Phase.ATTACHED) cancelAim()
            pop = (pop - dt * 4f).coerceAtLeast(0f)
            impact = (impact - dt * 3f).coerceAtLeast(0f)
            cameraBottom += (game.bottom - cameraBottom) * (1f - exp(-dt * 12f))
            cameraBottom = cameraBottom.coerceIn(game.bottom - 36f, game.bottom + 36f)
            if (game.phase == MochiGame.Phase.FLYING) {
                trail.addLast(MochiGame.Point(game.x, game.y))
                if (trail.size > 14) trail.removeFirst()
            } else if (trail.isNotEmpty()) trail.removeFirst()
            if (active) game.events.forEach { event ->
                val perfect = event.effect == MochiGame.Effect.PERFECT
                val caught = perfect || event.effect == MochiGame.Effect.CATCH || event.effect == MochiGame.Effect.RESCUE
                if (caught) pop = 1f
                if (caught || event.effect == MochiGame.Effect.GOLD || event.effect == MochiGame.Effect.REST) {
                    if (bursts.size < 8) bursts += Burst(event.x, event.y, event.effect == MochiGame.Effect.RESCUE)
                }
                if (event.effect == MochiGame.Effect.RESCUE) { impact = .6f; cameraBottom = game.bottom; trail.clear() }
                if (event.points > 0) popups += Popup(event.x, event.y,
                    if (event.bounces > 0) context.getString(if (perfect) R.string.mochi_perfect_bank_popup else R.string.mochi_bank_popup, event.points)
                    else if (perfect) context.getString(R.string.mochi_perfect_popup, game.combo, event.points)
                    else context.getString(R.string.mochi_points_popup, event.points))
                if (event.effect == MochiGame.Effect.BOUNCE) {
                    impact = max(impact, .16f)
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                repeat(12) {
                    val a = Random.nextFloat() * 6.283f
                    val speed = 30f + Random.nextFloat() * 70f
                    if (particles.size < 80) particles += Particle(event.x, event.y,
                        cos(a) * speed, sin(a) * speed, .6f,
                        when (event.effect) {
                            MochiGame.Effect.PERFECT, MochiGame.Effect.GOLD, MochiGame.Effect.SWEET -> 0xFFFFD77E.toInt()
                            MochiGame.Effect.RESCUE, MochiGame.Effect.BOUNCE -> 0xFFB3FFF2.toInt()
                            MochiGame.Effect.BREAK -> 0xFFE9958E.toInt()
                            else -> 0xFFD8F1BA.toInt()
                        })
                }
                if (caught) performHapticFeedback(if (perfect) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.CLOCK_TICK)
            }
            particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.life -= dt }
            particles.removeAll { it.life <= 0f }
            popups.forEach { it.life -= dt }
            popups.removeAll { it.life <= 0 }
            bursts.forEach { it.age += dt }
            bursts.removeAll { it.age >= .65f }
            val hud = listOf(game.score, game.heightMetres, game.combo, game.shots, game.attachedId,
                game.phase.ordinal, game.shields, game.magnet, game.grip, game.nectar, game.bankShots)
            if (hud != lastHud) { lastHud = hud; onChanged?.invoke() }
            invalidate()
            if (active && game.phase == MochiGame.Phase.OVER) {
                if (finishDelay < 0f) {
                    finishDelay = .65f
                    impact = 1f
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                finishDelay -= dt
                if (finishDelay <= 0f) { setRunning(false); onFinished?.invoke() }
            } else if (active && game.phase == MochiGame.Phase.CHOOSING) {
                setRunning(false)
                onRest?.invoke()
            }
            // Overlay callbacks may schedule this same runnable during the current frame.
            removeCallbacks(this)
            if (active) postOnAnimation(this) else if (decorating) postDelayed(this, 33)
        }
    }

    fun start(seed: Long = Random.nextLong(), easyMode: Boolean = false) {
        game = MochiGame(seed, easyMode)
        particles.clear()
        bursts.clear()
        popups.clear()
        trail.clear()
        cameraBottom = 0f
        currentBiome = 0; previousBiome = 0; biomeBlend = 1f
        decorating = false
        impact = 0f
        finishDelay = -1f
        pop = 0f
        lastHud = emptyList()
        cancelAim()
        setRunning(true)
        onChanged?.invoke()
    }

    fun setRunning(value: Boolean) {
        active = value
        lastFrame = 0
        removeCallbacks(frame)
        if (!value) cancelAim()
        if ((value || decorating) && isAttachedToWindow) postOnAnimation(frame)
        invalidate()
    }

    fun setDecorating(value: Boolean) {
        decorating = value
        lastFrame = 0L
        removeCallbacks(frame)
        if ((active || decorating) && isAttachedToWindow) postOnAnimation(frame)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (active || decorating) postOnAnimation(frame) }
    override fun onDetachedFromWindow() { decorating = false; setRunning(false); super.onDetachedFromWindow() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { cancelAim() }

    private fun cancelAim() {
        pointer = -1
        dragX = 0f; dragY = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (game.phase != MochiGame.Phase.ATTACHED || event.x < left ||
                    event.x > left + MochiGame.WIDTH * scale || event.y < top ||
                    event.y > top + MochiGame.HEIGHT * scale) return true
                pointer = event.getPointerId(0)
                downX = event.x; downY = event.y
                dragX = 0f; dragY = 0f
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index >= 0) {
                    dragX = (event.getX(index) - downX) / scale
                    dragY = (event.getY(index) - downY) / scale
                }
            }
            MotionEvent.ACTION_UP -> {
                if (pointer == event.getPointerId(event.actionIndex)) {
                    // Utiliser la position finale même si aucun MOVE n'a précédé le relâchement.
                    if (game.launch((event.x - downX) / scale, (event.y - downY) / scale)) onChanged?.invoke()
                    performClick()
                }
                cancelAim()
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) cancelAim()
            MotionEvent.ACTION_CANCEL -> cancelAim()
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF173A40.toInt())
        // The environment fills the screen; the aiming world stays clear of HUD and hint.
        canvas.save()
        val backdropScale = max(width / 420f, height / 700f)
        canvas.translate((width - 420f * backdropScale) / 2f, (height - 700f * backdropScale) / 2f)
        canvas.scale(backdropScale, backdropScale)
        art.background(canvas, cameraBottom, currentBiome, visualTime, previousBiome, biomeBlend)
        canvas.restore()
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, MochiGame.WIDTH, MochiGame.HEIGHT)
        canvas.translate(sin(impact * 35f) * impact * 3f, 0f)
        fun sy(y: Float) = MochiGame.HEIGHT - y + cameraBottom
        for (wall in game.walls) if (sy(wall.top) < 730f && sy(wall.bottom) > -30f) {
            art.wall(canvas, wall.left, sy(wall.top), wall.right, sy(wall.bottom), wall.material, wall.id)
        }
        for (flower in game.flowers) {
            if (!flower.alive) continue
            val y = sy(flower.y)
            if (y in -60f..780f) {
                val occupied = game.phase != MochiGame.Phase.FLYING && flower.id == game.attachedId
                art.flower(canvas, flower.at(game.time), y, flower.visited,
                    occupied, visualTime, flower.kind, flower.life / MochiGame.FRAGILE_TIME)
                if (flower.bonus && !flower.visited) art.sparkle(canvas, flower.at(game.time) + 30f, y - 8f, 5f, 0xFFFFD170.toInt())
            }
        }
        for (sweet in game.sweets) if (!sweet.taken) {
            val y = sy(sweet.y)
            if (y in -30f..730f) art.sweet(canvas, sweet.x, y, visualTime, sweet.golden)
        }
        for (hazard in game.hazards) {
            val y = sy(hazard.y)
            if (y in -45f..745f) art.hazard(canvas, hazard.at(game.time), y, hazard.kind, visualTime)
        }
        p.alpha = 255
        val aiming = pointer != -1
        val dx = if (aiming) dragX.coerceIn(-140f, 140f) * .12f else 0f
        val dy = if (aiming) dragY.coerceIn(-140f, 140f) * .12f else 0f
        if (aiming) {
            p.color = 0xFFCC7F9A.toInt()
            p.strokeWidth = 4f
            canvas.drawLine(game.x, sy(game.y), game.x + dx, sy(game.y) + dy, p)
            // Flèche de direction seulement ; la jauge fixe indique la puissance.
            val v = game.velocityForDrag(dragX, dragY)
            val speed = hypot(v.x, v.y)
            if (speed > 65f) {
                val power = (speed / (MochiGame.MAX_SPEED * game.launchPower)).coerceIn(0f, 1f)
                val length = 24f + power * 20f
                val ux = v.x / speed; val uy = -v.y / speed
                val tx = game.x + ux * length; val ty = sy(game.y) + uy * length
                p.color = 0xFFFFE6A9.toInt(); p.strokeWidth = 2.5f
                canvas.drawLine(game.x + ux * 27, sy(game.y) + uy * 27, tx, ty, p)
                canvas.drawLine(tx, ty, tx - ux * 7 - uy * 4, ty - uy * 7 + ux * 4, p)
                canvas.drawLine(tx, ty, tx - ux * 7 + uy * 4, ty - uy * 7 - ux * 4, p)
            }
        }
        art.trail(canvas, trail, cameraBottom)
        bursts.forEach { art.burst(canvas, it.x, sy(it.y), it.age / .65f, it.blue) }
        p.alpha = 255
        for ((index, particle) in particles.withIndex()) {
            p.color = particle.color
            p.alpha = (particle.life / .6f * 255).toInt().coerceIn(0, 255)
            if (index % 3 == 0) art.sparkle(canvas, particle.x, sy(particle.y), particle.life * 8,
                (p.alpha shl 24) or (particle.color and 0xFFFFFF))
            else canvas.drawCircle(particle.x, sy(particle.y), particle.life * 4f, p)
        }
        p.alpha = 255
        val stretch = when {
            aiming -> 1f + min(hypot(dragX, dragY) / 650f, .22f)
            game.phase == MochiGame.Phase.FLYING -> 1.05f + min(abs(game.vy) / 2400f, .2f)
            else -> 1f - pop * .18f + sin(visualTime * 3) * .025f
        }
        art.body(canvas, game.x + dx, sy(game.y) + dy, stretch,
            (game.vx / 14f).coerceIn(-20f, 20f), visualTime, game.phase == MochiGame.Phase.OVER,
            game.shields > 0, if (aiming) -dragX / 100f else game.vx / 300f,
            // Le corps opaque du sprite couvre ~3/4 de sa largeur : il suit le cercle physique.
            size = MochiGame.RADIUS * 2.7f)
        art.foreground(canvas, cameraBottom, visualTime)
        p.textAlign = Paint.Align.CENTER
        p.typeface = popupTypeface
        p.textSize = 19f
        for (popup in popups) {
            val yy = sy(popup.y) - 34f - (1f - popup.life) * 24f
            val halfWidth = p.measureText(popup.text) * .5f + 5f
            val xx = popup.x.coerceIn(halfWidth, MochiGame.WIDTH - halfWidth)
            p.color = 0xFF345B50.toInt(); p.alpha = (popup.life * 255).toInt().coerceIn(0, 255)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f
            canvas.drawText(popup.text, xx, yy, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFFFEFC7.toInt(); p.alpha = (popup.life * 255).toInt().coerceIn(0, 255)
            canvas.drawText(popup.text, xx, yy, p)
        }
        p.alpha = 255
        // Le bord inférieur rappelle la chute sans cacher les prises de récupération.
        if (game.bottom > 0) {
            p.color = 0x44A65E7D
            canvas.drawRect(0f, sy(game.bottom) - 5f, MochiGame.WIDTH, 700f, p)
        }
        canvas.restore()
        if (active) drawPowerGauge(canvas)
    }

    private fun drawPowerGauge(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width - gaugeSpace * .5f
        val y0 = top + min(90f * d, MochiGame.HEIGHT * scale * .18f)
        val h = min(144f * d, MochiGame.HEIGHT * scale * .32f)
        val v = game.velocityForDrag(dragX, dragY)
        val power = if (pointer == -1) 0f else (hypot(v.x, v.y) / (MochiGame.MAX_SPEED * game.launchPower)).coerceIn(0f, 1f)
        p.style = Paint.Style.FILL; p.alpha = 255
        p.color = 0xED183E3A.toInt()
        canvas.drawRoundRect(cx - 21f * d, y0 - 25f * d, cx + 21f * d, y0 + h + 29f * d, 12f * d, 12f * d, p)
        p.color = 0xFF56746A.toInt()
        canvas.drawRoundRect(cx - 7f * d, y0, cx + 7f * d, y0 + h, 5f * d, 5f * d, p)
        p.color = when { power > .9f -> 0xFFFFAD73.toInt(); power > .6f -> 0xFFFFDA86.toInt(); else -> 0xFF91E7B9.toInt() }
        if (power > 0f) canvas.drawRoundRect(cx - 7f * d, y0 + h * (1f - power), cx + 7f * d, y0 + h, 5f * d, 5f * d, p)
        p.color = 0xBBDDECCB.toInt(); p.strokeWidth = d
        for (i in 0..4) {
            val yy = y0 + h * i / 4f
            canvas.drawLine(cx - 12f * d, yy, cx - 8f * d, yy, p)
            canvas.drawLine(cx + 8f * d, yy, cx + 12f * d, yy, p)
        }
        p.color = 0xFFFFF0CF.toInt(); p.textAlign = Paint.Align.CENTER; p.typeface = popupTypeface
        p.textSize = 11f * d
        canvas.drawText(context.getString(R.string.mochi_power_label), cx, y0 - 10f * d, p)
        p.textSize = 13f * d
        canvas.drawText(context.getString(R.string.mochi_power_percent, (power * 100).roundToInt()), cx, y0 + h + 19f * d, p)
    }
}
