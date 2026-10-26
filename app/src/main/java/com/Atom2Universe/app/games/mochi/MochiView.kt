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
    private var preview = MochiGame.Preview.EMPTY
    private var demo = 0f
    private var demoPreview = MochiGame.Preview.EMPTY
    private var pop = 0f
    private var lastHud = emptyList<Int>()
    private var cameraBottom = 0f
    private var impact = 0f
    private var finishDelay = -1f
    private data class Popup(val x: Float, val y: Float, val text: String, var life: Float = 1f, val big: Boolean = false)
    private val popups = ArrayList<Popup>()
    private val trail = ArrayDeque<MochiGame.Point>()
    private data class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val color: Int)
    private val particles = ArrayList<Particle>()
    private data class Burst(val x: Float, val y: Float, val blue: Boolean, var age: Float = 0f)
    private val bursts = ArrayList<Burst>()
    private val availableHeight get() = (height - viewportTop - viewportBottom).coerceAtLeast(1f)
    private val scale get() = min(width / MochiGame.WIDTH, availableHeight / MochiGame.HEIGHT).coerceAtLeast(.001f)
    private val left get() = (width - MochiGame.WIDTH * scale) / 2
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
                popups += Popup(MochiGame.WIDTH / 2, game.y + 150f, context.getString(when (currentBiome) {
                    1 -> R.string.mochi_biome_sunset
                    2 -> R.string.mochi_biome_moon
                    else -> R.string.mochi_biome_dawn
                }), 2.2f, big = true)
            }
            biomeBlend = (biomeBlend + dt * .7f).coerceAtMost(1f)
            if (pointer != -1 && game.phase != MochiGame.Phase.ATTACHED) cancelAim()
            // Les prises mobiles et les abeilles bougent pendant la visée : l'arc suit à chaque image.
            preview = if (pointer != -1) game.preview(dragX, dragY) else MochiGame.Preview.EMPTY
            if (active && game.shots == 0 && pointer == -1 && game.phase == MochiGame.Phase.ATTACHED) {
                demo += dt
                val drag = demoDrag()
                val progress = demoProgress()
                demoPreview = if (progress > .05f) game.preview(drag.x * progress, drag.y * progress) else MochiGame.Preview.EMPTY
            } else demoPreview = MochiGame.Preview.EMPTY
            pop = (pop - dt * 4f).coerceAtLeast(0f)
            impact = (impact - dt * 3f).coerceAtLeast(0f)
            cameraBottom += (game.bottom - cameraBottom) * (1f - exp(-dt * 12f))
            cameraBottom = cameraBottom.coerceIn(game.bottom - 36f, game.bottom + 36f)
            if (game.phase == MochiGame.Phase.FLYING) {
                trail.addLast(MochiGame.Point(game.x, game.y))
                if (trail.size > 14) trail.removeFirst()
            } else if (trail.isNotEmpty()) trail.removeFirst()
            if (active) game.events.forEach { event -> react(event) }
            particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.life -= dt }
            particles.removeAll { it.life <= 0f }
            popups.forEach { it.life -= dt }
            popups.removeAll { it.life <= 0 }
            bursts.forEach { it.age += dt }
            bursts.removeAll { it.age >= .65f }
            val hud = listOf(game.score, game.heightMetres, game.combo, game.shots, game.attachedId,
                game.phase.ordinal, game.bubbles, game.peaches)
            if (hud != lastHud) { lastHud = hud; onChanged?.invoke() }
            invalidate()
            if (active && game.phase == MochiGame.Phase.OVER) {
                if (finishDelay < 0f) {
                    finishDelay = .8f
                    impact = 1f
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                finishDelay -= dt
                if (finishDelay <= 0f) { setRunning(false); onFinished?.invoke() }
            }
            // Overlay callbacks may schedule this same runnable during the current frame.
            removeCallbacks(this)
            if (active) postOnAnimation(this) else if (decorating) postDelayed(this, 33)
        }
    }

    private fun react(event: MochiGame.Spark) {
        val perfect = event.effect == MochiGame.Effect.PERFECT
        val caught = perfect || event.effect == MochiGame.Effect.CATCH || event.effect == MochiGame.Effect.RESCUE
        if (caught) pop = 1f
        val blue = event.effect == MochiGame.Effect.RESCUE || event.effect == MochiGame.Effect.BUBBLE
        if (caught || event.effect == MochiGame.Effect.GOLD || event.effect == MochiGame.Effect.REST || event.effect == MochiGame.Effect.BUBBLE) {
            if (bursts.size < 8) bursts += Burst(event.x, event.y, blue)
        }
        if (event.effect == MochiGame.Effect.RESCUE) { impact = .6f; cameraBottom = game.bottom; trail.clear() }
        if (event.effect == MochiGame.Effect.WILT || event.effect == MochiGame.Effect.POP) {
            impact = max(impact, .3f)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        if (event.points > 0) popups += Popup(event.x, event.y,
            if (event.bounces > 0) context.getString(if (perfect) R.string.mochi_perfect_bank_popup else R.string.mochi_bank_popup, event.points)
            else if (perfect) context.getString(R.string.mochi_perfect_popup, game.combo, event.points)
            else context.getString(R.string.mochi_points_popup, event.points))
        if (event.effect == MochiGame.Effect.BUBBLE) popups += Popup(event.x, event.y + 30f, context.getString(R.string.mochi_bubble_popup), 1.3f)
        if (event.effect == MochiGame.Effect.BOUNCE) {
            impact = max(impact, .16f)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        repeat(if (event.effect == MochiGame.Effect.WILT) 24 else 12) {
            val a = Random.nextFloat() * 6.283f
            val speed = 30f + Random.nextFloat() * 70f
            if (particles.size < 90) particles += Particle(event.x, event.y,
                cos(a) * speed, sin(a) * speed - if (event.effect == MochiGame.Effect.WILT) 40f else 0f, .7f,
                when (event.effect) {
                    MochiGame.Effect.PERFECT, MochiGame.Effect.GOLD, MochiGame.Effect.SWEET -> 0xFFFFD77E.toInt()
                    MochiGame.Effect.RESCUE, MochiGame.Effect.BOUNCE, MochiGame.Effect.BUBBLE, MochiGame.Effect.POP -> 0xFFB3FFF2.toInt()
                    MochiGame.Effect.WILT -> 0xFFF2A6A0.toInt()
                    else -> 0xFFD8F1BA.toInt()
                })
        }
        if (caught) performHapticFeedback(if (perfect) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.CLOCK_TICK)
    }

    fun start(seed: Long = Random.nextLong()) {
        game = MochiGame(seed)
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
        demo = 0f
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
        preview = MochiGame.Preview.EMPTY
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (game.phase != MochiGame.Phase.ATTACHED) return true
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
                    val fromBubble = game.inBubble
                    if (game.launch((event.x - downX) / scale, (event.y - downY) / scale)) {
                        if (fromBubble) react(MochiGame.Spark(game.x, game.y, MochiGame.Effect.POP))
                        onChanged?.invoke()
                    }
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

    /** Le geste de démonstration vise réellement la première fleur. */
    private fun demoDrag(): MochiGame.Point {
        val from = game.attachedFlower ?: return MochiGame.Point(-50f, 110f)
        val target = game.flowers.firstOrNull { it.id == 1 } ?: return MochiGame.Point(-50f, 110f)
        val v = MochiGame.routeVelocity(from, target)
        return MochiGame.Point(-v.x / MochiGame.DRAG_POWER, v.y / MochiGame.DRAG_POWER)
    }

    /** Cycle de 3,2 s : le doigt tire pendant 1,4 s, tient, puis s'efface. */
    private fun demoProgress(): Float {
        val t = demo % 3.2f
        return when {
            t < 1.4f -> (1f - cos(t / 1.4f * PI.toFloat())) / 2f
            t < 2.2f -> 1f
            else -> 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF173A40.toInt())
        // The environment fills the screen; the aiming world stays clear of the HUD.
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
        val shownPreview = if (pointer != -1) preview else demoPreview
        for (flower in game.flowers) {
            if (!flower.alive) continue
            val y = sy(flower.y)
            if (y in -60f..780f) {
                val occupied = game.phase != MochiGame.Phase.FLYING && flower.id == game.attachedId
                if (shownPreview.outcome == MochiGame.Outcome.LAND && shownPreview.flowerId == flower.id) {
                    art.target(canvas, flower.at(game.time), y, visualTime)
                }
                art.flower(canvas, flower.at(game.time), y, flower.visited, occupied, visualTime, flower.kind,
                    if (occupied) game.wiltFraction else -1f, flower.bonus)
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
        val demoing = !aiming && demoPreview.points.isNotEmpty()
        val demoDrag = if (demoing) demoDrag() else MochiGame.Point(0f, 0f)
        val progress = if (demoing) demoProgress() else 0f
        val pullX = if (aiming) dragX else demoDrag.x * progress
        val pullY = if (aiming) dragY else demoDrag.y * progress
        val dx = pullX.coerceIn(-140f, 140f) * .12f
        val dy = pullY.coerceIn(-140f, 140f) * .12f
        if (aiming || demoing) {
            p.color = 0xFFCC7F9A.toInt()
            p.strokeWidth = 4f
            canvas.drawLine(game.x, sy(game.y), game.x + dx, sy(game.y) + dy, p)
        }
        drawArc(canvas, shownPreview, if (aiming) 1f else .55f, ::sy)
        if (demoing) art.finger(canvas, game.x + pullX, sy(game.y) + pullY, game.x, sy(game.y),
            1f - ((demo % 3.2f - 1.8f) / .4f).coerceIn(0f, 1f))
        art.trail(canvas, trail, cameraBottom)
        bursts.forEach { art.burst(canvas, it.x, sy(it.y), it.age / .65f, it.blue) }
        p.alpha = 255
        for ((index, particle) in particles.withIndex()) {
            p.color = particle.color
            p.alpha = (particle.life / .7f * 255).toInt().coerceIn(0, 255)
            if (index % 3 == 0) art.sparkle(canvas, particle.x, sy(particle.y), particle.life * 8,
                (p.alpha shl 24) or (particle.color and 0xFFFFFF))
            else canvas.drawCircle(particle.x, sy(particle.y), particle.life * 4f, p)
        }
        p.alpha = 255
        val stretch = when {
            aiming || demoing -> 1f + min(hypot(pullX, pullY) / 650f, .22f)
            game.phase == MochiGame.Phase.FLYING -> 1.05f + min(abs(game.vy) / 2400f, .2f)
            else -> 1f - pop * .18f + sin(visualTime * 3) * .025f
        }
        if (game.inBubble) art.bubble(canvas, game.x + dx, sy(game.y) + dy, visualTime, game.wiltFraction)
        art.body(canvas, game.x + dx, sy(game.y) + dy, stretch,
            (game.vx / 14f).coerceIn(-20f, 20f), visualTime, game.phase == MochiGame.Phase.OVER,
            game.bubbles > 0 && !game.inBubble, if (aiming || demoing) -pullX / 100f else game.vx / 300f,
            // Le corps opaque du sprite couvre ~3/4 de sa largeur : il suit le cercle physique.
            size = MochiGame.RADIUS * 2.7f)
        art.foreground(canvas, cameraBottom, visualTime)
        p.textAlign = Paint.Align.CENTER
        p.typeface = popupTypeface
        for (popup in popups) {
            p.textSize = if (popup.big) 26f else 19f
            val fade = if (popup.big) min(popup.life, 1f) else popup.life
            val yy = sy(popup.y) - 34f - (1f - fade) * 24f
            val halfWidth = p.measureText(popup.text) * .5f + 5f
            val xx = popup.x.coerceIn(halfWidth, MochiGame.WIDTH - halfWidth)
            p.color = 0xFF345B50.toInt(); p.alpha = (fade * 255).toInt().coerceIn(0, 255)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f
            canvas.drawText(popup.text, xx, yy, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFFFEFC7.toInt(); p.alpha = (fade * 255).toInt().coerceIn(0, 255)
            canvas.drawText(popup.text, xx, yy, p)
        }
        p.alpha = 255
        // Le bord inférieur rappelle la chute sans cacher les prises de récupération.
        if (game.bottom > 0) {
            p.color = 0x44A65E7D
            canvas.drawRect(0f, sy(game.bottom) - 5f, MochiGame.WIDTH, 700f, p)
        }
        canvas.restore()
    }

    /** Pointillés le long de l'arc, puis le verdict : halo sur la fleur ou croix rouge. */
    private fun drawArc(canvas: Canvas, arc: MochiGame.Preview, strength: Float, sy: (Float) -> Float) {
        val points = arc.points
        if (points.isEmpty()) return
        p.style = Paint.Style.FILL
        val danger = arc.outcome == MochiGame.Outcome.DANGER
        for ((index, point) in points.withIndex()) {
            val along = index.toFloat() / points.size
            val radius = 4.2f - along * 1.8f
            val alpha = ((1f - along * .55f) * 255 * strength).toInt().coerceIn(0, 255)
            p.color = 0xFF2E5A55.toInt(); p.alpha = alpha / 2
            canvas.drawCircle(point.x, sy(point.y), radius + 1.6f, p)
            p.color = if (danger && along > .7f) 0xFFFFA5B5.toInt() else 0xFFFFF4D6.toInt(); p.alpha = alpha
            canvas.drawCircle(point.x, sy(point.y), radius, p)
        }
        p.alpha = 255
        if (danger) points.lastOrNull()?.let { art.cross(canvas, it.x, sy(it.y), visualTime) }
    }
}
