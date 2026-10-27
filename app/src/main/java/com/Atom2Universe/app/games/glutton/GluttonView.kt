package com.Atom2Universe.app.games.glutton

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Family
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import kotlin.math.*
import kotlin.random.Random

/** One hardware-accelerated View, one frame callback, bounded effects and visible-sprite culling. */
class GluttonView(context: Context) : View(context) {
    var game = GluttonGame(); private set
    var onHudChanged: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onMilestone: ((Float) -> Unit)? = null
    var onStreak: ((Family) -> Unit)? = null
    var onSceneCleared: ((Int) -> Unit)? = null
    var onSceneChanged: (() -> Unit)? = null
    /** Localised name of a swallowed thing, shown for big bites. */
    var nameOf: ((Prop) -> String)? = null
    var haptics = true
    var sound = true
    private var audio: GluttonAudio? = null
    private var lastSound = -1f
    private val atlas = GluttonAtlas(context)
    internal val art = GluttonArt(atlas)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GluttonArt.INK; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bounds = RectF()
    private val ring = RectF()
    private val random = Random(71)
    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var total = 1f; var radius = 2f; var color = 0
    }
    private class Bite { var x = 0f; var y = 0f; var life = 0f; var prop: Prop? = null; var radius = 0f; var angle = 0f }
    private class Label { var text = ""; var x = 0f; var y = 0f; var life = 0f; var scale = 1f }
    private val particles = Array(128) { Particle() }
    private val bites = Array(28) { Bite() }
    private val labels = Array(4) { Label() }
    private var particleCursor = 0
    private var biteCursor = 0
    private var labelCursor = 0
    private var running = false
    private var lastFrame = 0L
    private var lastHud = -1f
    private var lastHaptic = -1f
    private var finishDelay = -1f
    private var transition = -1f
    private var swapped = false
    private var shockwave = 0f
    private var pointer = -1
    private var originX = 0f
    private var originY = 0f
    private var fingerX = 0f
    private var fingerY = 0f
    private var topInset = 0f
    private var bottomInset = 0f
    private var cameraX = game.x
    private var cameraY = game.y
    private var zoom = 0f
    private var pop = 0f
    private var impact = 0f
    private var visualTime = 0f
    private var hasSteered = false
    private var nearest: GluttonGame.Item? = null
    private var nextHint = 0f
    private val feverText = context.getString(R.string.glutton_fever)
    private val hintText = context.getString(R.string.glutton_drag)
    val isRunning get() = running
    val hasInput get() = pointer != -1
    val inTransition get() = transition >= 0f
    private val density get() = resources.displayMetrics.density
    private val playHeight get() = (height - topInset - bottomInset).coerceAtLeast(1f)
    private val centerY get() = topInset + playHeight / 2

    init {
        isClickable = true
        contentDescription = context.getString(R.string.glutton_touch)
    }

    fun setViewportInsets(top: Int, bottom: Int) {
        if (topInset == top.toFloat() && bottomInset == bottom.toFloat()) return
        topInset = top.toFloat(); bottomInset = bottom.toFloat(); cancelInput(); zoom = 0f
        cameraX = game.x; cameraY = game.y; invalidate()
    }

    fun start(seed: Long = Random.nextLong(), scene: Int = 0) {
        game = GluttonGame(seed, scene)
        resetScene()
    }

    private fun resetScene() {
        particles.forEach { it.life = 0f }; bites.forEach { it.life = 0f }; labels.forEach { it.life = 0f }
        cameraX = game.x; cameraY = game.y; zoom = 0f
        pop = 0f; impact = 0f; visualTime = 0f
        hasSteered = false; nearest = null; nextHint = 0f
        finishDelay = -1f; transition = -1f; swapped = false; shockwave = 0f; lastHud = -1f; lastHaptic = -1f; lastSound = -1f
        bounds.set(0f, 0f, game.scene.width, game.scene.height)
        atlas.prepare(game.scene)
        setRunning(true)
        onHudChanged?.invoke()
    }

    fun setRunning(value: Boolean) {
        running = value
        cancelInput(); lastFrame = 0L
        removeCallbacks(frame)
        if (!value) audio?.pause()
        if (value && isAttachedToWindow) postOnAnimation(frame)
        invalidate()
    }

    fun suction(): Boolean {
        if (!running || !game.activateBurst()) return false
        emit(game.x, game.y, 22, GluttonArt.MINT)
        shockwave = 1f
        haptic(HapticFeedbackConstants.LONG_PRESS)
        if (sound) audio?.play(2)
        onHudChanged?.invoke()
        return true
    }

    fun cancelInput() { pointer = -1; game.steer(0f, 0f) }

    private val frame = object : Runnable {
        override fun run() {
            if (!running || !isAttachedToWindow) return
            val now = SystemClock.elapsedRealtimeNanos()
            val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, .05f)
            lastFrame = now; visualTime += dt
            if (transition >= 0f) advanceTransition(dt) else {
                game.update(dt)
                for (event in game.events) react(event)
            }
            pop = (pop - dt * 3.8f).coerceAtLeast(0f)
            impact = (impact - dt * 3f).coerceAtLeast(0f)
            shockwave = (shockwave - dt * 2.2f).coerceAtLeast(0f)
            for (particle in particles) if (particle.life > 0f) {
                particle.life -= dt; particle.x += particle.vx * dt; particle.y += particle.vy * dt
                particle.vx *= (1f - dt * 2f); particle.vy *= (1f - dt * 2f)
            }
            for (bite in bites) if (bite.life > 0f) {
                bite.life -= dt
                val lerp = 1f - exp(-dt * 17f)
                bite.x += (game.x - bite.x) * lerp; bite.y += (game.y - bite.y) * lerp
            }
            for (label in labels) if (label.life > 0f) { label.life -= dt; label.y -= dt * 18f / zoom.coerceAtLeast(.01f) }
            val easing = 1f - exp(-dt * 7f)
            cameraX += (game.x + game.vx * .12f - cameraX) * easing
            cameraY += (game.y + game.vy * .12f - cameraY) * easing
            updateCamera(dt)
            if (game.elapsed >= nextHint) { nearest = game.nearestFood(); nextHint = game.elapsed + .3f }
            if (game.elapsed - lastHud > .1f || game.finished) { lastHud = game.elapsed; onHudChanged?.invoke() }
            if (finishDelay >= 0f) {
                finishDelay += dt
                if (finishDelay >= .65f) { setRunning(false); onFinished?.invoke(); return }
            }
            invalidate(); postOnAnimation(this)
        }
    }

    /** Scene change: dive into the gobbler, swap the world underneath, then ease back out to see how small it is now. */
    private fun advanceTransition(dt: Float) {
        transition += dt
        if (!swapped && transition >= TRANSITION_SWAP) {
            swapped = true
            if (game.advanceScene()) {
                bounds.set(0f, 0f, game.scene.width, game.scene.height)
                atlas.prepare(game.scene)
                cameraX = game.x; cameraY = game.y; zoom = width / (game.radius * 3f)
                particles.forEach { it.life = 0f }; bites.forEach { it.life = 0f }; labels.forEach { it.life = 0f }
                nearest = null; nextHint = 0f
                onSceneChanged?.invoke()
            }
        }
        if (transition >= TRANSITION_TOTAL) { transition = -1f; swapped = false; cancelInput() }
    }

    private fun updateCamera(dt: Float) {
        if (width <= 0 || height <= 0) return
        var target = max(width / (game.radius * 14f), max(width / game.scene.width, playHeight / game.scene.height))
        if (transition in 0f..TRANSITION_SWAP) target *= 1f + transition / TRANSITION_SWAP * 2.5f
        zoom = if (zoom <= 0f) target else zoom + (target - zoom) * (1f - exp(-dt * if (transition >= 0f) 5f else 2.4f))
        val halfW = width / (2 * zoom); val halfH = playHeight / (2 * zoom)
        cameraX = cameraX.coerceIn(halfW, max(halfW, game.scene.width - halfW))
        cameraY = cameraY.coerceIn(halfH, max(halfH, game.scene.height - halfH))
    }

    private fun react(event: GluttonGame.Event) {
        when (event.effect) {
            GluttonGame.Effect.EAT -> {
                val item = event.item ?: return
                val big = game.isBigBite(item)
                pop = if (big) 1.1f else .6f
                val bite = bites[biteCursor++ % bites.size]
                bite.prop = item.prop; bite.x = event.x; bite.y = event.y; bite.life = .24f
                bite.radius = item.radius; bite.angle = item.angle
                emit(event.x, event.y, if (big) 12 else 3, if (game.fever > 0f) GluttonArt.GOLD else GluttonArt.MINT)
                if (big) {
                    val label = labels[labelCursor++ % labels.size]
                    label.text = nameOf?.invoke(item.prop) ?: ""; label.x = event.x; label.y = event.y - item.radius
                    label.life = 1.3f; label.scale = (item.radius / game.radius).coerceIn(.6f, 1.2f)
                }
                if (sound && game.elapsed - lastSound > .07f) {
                    lastSound = game.elapsed; audio?.play(0, (if (big) .7f else .9f) + game.multiplier * .08f)
                }
                if (game.elapsed - lastHaptic > .085f) {
                    lastHaptic = game.elapsed
                    haptic(if (big) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            GluttonGame.Effect.MILESTONE -> {
                pop = 1.5f; emit(game.x, game.y, 32, GluttonArt.GOLD)
                haptic(HapticFeedbackConstants.LONG_PRESS)
                if (sound) audio?.play(1)
                onMilestone?.invoke(GluttonGame.MILESTONES[event.value])
            }
            GluttonGame.Effect.KNOCK -> {
                impact = 1f; pop = -.6f; haptic(HapticFeedbackConstants.LONG_PRESS)
                emit(game.x, game.y, 10, GluttonArt.CORAL)
                if (sound) audio?.play(3)
            }
            GluttonGame.Effect.FEVER -> { emit(game.x, game.y, 24, GluttonArt.GOLD); haptic(HapticFeedbackConstants.LONG_PRESS) }
            GluttonGame.Effect.TREASURE -> { emit(event.x, event.y, 24, GluttonArt.GOLD); if (sound) audio?.play(1, 1.3f) }
            GluttonGame.Effect.PREY -> {
                emit(event.x, event.y, 20, GluttonArt.MINT); pop = 1.2f
                haptic(HapticFeedbackConstants.CONTEXT_CLICK); if (sound) audio?.play(0, .7f)
            }
            GluttonGame.Effect.STREAK -> { emit(game.x, game.y, 18, GluttonArt.GOLD); event.family?.let { onStreak?.invoke(it) } }
            GluttonGame.Effect.SCENE_CLEAR -> {
                emit(game.x, game.y, 42, GluttonArt.GOLD); cancelInput()
                if (sound) audio?.play(1, .85f)
                onSceneCleared?.invoke(event.value)
                if (!game.finished) { transition = 0f; swapped = false }
            }
            GluttonGame.Effect.FINISH -> { finishDelay = 0f; cancelInput(); haptic(HapticFeedbackConstants.LONG_PRESS) }
            GluttonGame.Effect.BURST -> Unit
        }
    }

    private fun emit(x: Float, y: Float, count: Int, color: Int) {
        repeat(count) {
            val particle = particles[particleCursor++ % particles.size]
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = (40f + random.nextFloat() * 110f) * (game.radius / 16f).coerceIn(1f, 6f)
            particle.x = x; particle.y = y; particle.vx = cos(angle) * speed; particle.vy = sin(angle) * speed
            particle.total = .3f + random.nextFloat() * .4f; particle.life = particle.total
            particle.radius = (2f + random.nextFloat() * 3f) * (game.radius / 16f).coerceIn(1f, 6f); particle.color = color
        }
    }

    private fun haptic(type: Int) { if (haptics) performHapticFeedback(type) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(0xFFEEDACE.toInt())
        if (width == 0 || height == 0) return
        if (zoom <= 0f) updateCamera(0f)
        c.save(); c.clipRect(0f, topInset, width.toFloat(), height - bottomInset)
        val shakeX = if (impact > 0f) sin(visualTime * 85f) * impact * 5f * density else 0f
        c.translate(width / 2f - cameraX * zoom + shakeX, centerY - cameraY * zoom)
        c.scale(zoom, zoom)
        p.color = Color.WHITE; p.alpha = 255; p.style = Paint.Style.FILL
        c.drawBitmap(art.floor(game.scene), null, bounds, p)
        val left = cameraX - width / (2 * zoom) - 210f; val right = cameraX + width / (2 * zoom) + 210f
        val top = cameraY - playHeight / (2 * zoom) - 210f; val bottom = cameraY + playHeight / (2 * zoom) + 210f
        val r = game.radius
        for (pass in 0..1) for (item in game.items) {
            if (!item.active || item.moves != (pass == 1)) continue
            if (item.x < left || item.x > right || item.y < top || item.y > bottom) continue
            val edible = game.canEat(item)
            val special = item.prop.goal || item.prop.treasure
            // Only the landmark, the treasures and the really rewarding bites get a halo; the rest stays clean.
            if (special || (edible && item.radius >= r * GluttonGame.BIG_BITE)) {
                p.color = if (special) 0x55FFD46F else 0x4450BA99
                c.drawCircle(item.x, item.y, item.radius + 7f, p)
                p.color = if (special) GluttonArt.GOLD else 0x997BE4AC.toInt()
                p.style = Paint.Style.STROKE; p.strokeWidth = if (item.prop.goal) 4f else 2f
                c.drawCircle(item.x, item.y, item.radius + 7f, p); p.style = Paint.Style.FILL
            }
            val alpha = if (item.cooldown > 0f) 150 + (sin(visualTime * 30f) * 60f).toInt() else 255
            art.item(c, item.prop, item.x, item.y, item.radius, if (item.moves) facing(item) else item.angle, alpha)
            if (item.prop.goal || item.prop.treasure) art.star(c, item.x, item.y - item.radius - 19f, 15f, GluttonArt.GOLD)
            if (item.moves) {
                if (item.stunned > 0f) for (i in 0..2) {
                    val a = visualTime * 3f + i * 2.1f
                    art.star(c, item.x + cos(a) * item.radius, item.y - item.radius + sin(a) * item.radius * .3f, item.radius * .18f + 3f, GluttonArt.GOLD)
                } else if (item.alert) art.alarm(c, item.x, item.y - item.radius - 6f, item.radius * .55f + 10f, !edible)
            }
        }
        drawSuction(c)
        if (shockwave > 0f) {
            p.color = GluttonArt.MINT; p.alpha = (shockwave * 220).toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 5f
            c.drawCircle(game.x, game.y, r + (1f - shockwave) * (game.suctionRadius + 40f), p)
            p.alpha = 255; p.style = Paint.Style.FILL
        }
        for (bite in bites) if (bite.life > 0f) bite.prop?.let { art.item(c, it, bite.x, bite.y,
            bite.radius * (bite.life / .24f).coerceIn(0f, 1f), bite.angle + (1f - bite.life / .24f) * 150f) }
        drawStuck(c)
        val speed = hypot(game.vx, game.vy)
        art.body(c, game.x, game.y, r, visualTime,
            game.vx / game.moveSpeed, game.vy / game.moveSpeed, pop, game.burst > 0f || pop > .2f,
            impact > .2f, game.fever > 0f)
        if (game.invulnerable > 0f && impact > 0f) {
            for (i in 0..2) art.star(c, game.x + cos(visualTime * 5f + i * 2.1f) * (r + 12f),
                game.y - r - 10f + sin(visualTime * 5f + i * 2.1f) * 8f, 5f, GluttonArt.GOLD)
        }
        for (particle in particles) if (particle.life > 0f) {
            p.color = particle.color; p.alpha = (255 * particle.life / particle.total).toInt().coerceIn(0, 255)
            c.drawCircle(particle.x, particle.y, particle.radius, p)
        }
        p.alpha = 255
        for (label in labels) if (label.life > 0f && label.text.isNotEmpty()) {
            textPaint.textSize = 16f * density / zoom * label.scale
            textPaint.color = Color.WHITE; textPaint.alpha = (255 * (label.life / 1.3f).coerceIn(0f, 1f)).toInt()
            textPaint.style = Paint.Style.STROKE; textPaint.strokeWidth = 4f * density / zoom
            c.drawText(label.text, label.x, label.y, textPaint)
            textPaint.style = Paint.Style.FILL; textPaint.color = GluttonArt.INK
            textPaint.alpha = (255 * (label.life / 1.3f).coerceIn(0f, 1f)).toInt()
            c.drawText(label.text, label.x, label.y, textPaint); textPaint.alpha = 255
        }
        drawDirectionHint(c, speed)
        c.restore()
        if (transition >= 0f) {
            val t = transition
            val alpha = if (t < TRANSITION_SWAP) (t / TRANSITION_SWAP) else (1f - (t - TRANSITION_SWAP) / (TRANSITION_TOTAL - TRANSITION_SWAP))
            p.color = 0xFFFFF5E7.toInt(); p.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p); p.alpha = 255
        }
        drawGoalMarker(c)
        drawMap(c)
        if (game.fever > 0f) {
            textPaint.textSize = 17f * density; textPaint.color = 0xFFB87A3B.toInt()
            c.drawText(feverText, width / 2f, topInset + 26f * density, textPaint)
        }
        if (!hasSteered && running && game.elapsed < 7f) {
            textPaint.textSize = 14f * density; textPaint.color = GluttonArt.INK
            c.drawText(hintText, width / 2f, centerY + r * zoom + 50f * density, textPaint)
        }
        if (pointer != -1) {
            p.color = 0x33405161; c.drawCircle(originX, originY, 42f * density, p)
            p.color = 0x88FFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * density
            c.drawCircle(originX, originY, 42f * density, p); p.style = Paint.Style.FILL
            p.color = 0xAA55C7AF.toInt(); c.drawCircle(fingerX, fingerY, 18f * density, p)
        }
    }

    private fun facing(item: GluttonGame.Item): Float {
        if (abs(item.vx) + abs(item.vy) < 5f) return item.angle
        return if (item.vx < 0f) -8f else 8f
    }

    /** Swallowed things ride on the body and roll with it, shrinking as the gobbler outgrows them. */
    private fun drawStuck(c: Canvas) {
        val r = game.radius
        for ((index, stuck) in game.stuck.withIndex()) {
            val item = stuck.item
            val drawn = min(item.radius * .62f, r * .42f)
            if (drawn < r * .08f) continue
            val a = stuck.angle + game.spin
            val distance = r * .68f + index % 3 * r * .07f
            art.item(c, item.prop, game.x + cos(a) * distance, game.y + sin(a) * distance, drawn, a * 180f / PI.toFloat() + 90f)
        }
    }

    private fun drawSuction(c: Canvas) {
        if (!running || game.finished) return
        val strong = game.burst > 0f || game.fever > 0f
        p.color = if (game.fever > 0f) GluttonArt.GOLD else GluttonArt.MINT
        p.style = Paint.Style.STROKE; p.strokeWidth = if (strong) 3f else 1.6f
        for (i in 0 until if (strong) 3 else 1) {
            val phase = (visualTime * 1.6f + i / 3f) % 1f
            val r = game.radius + (game.suctionRadius - game.radius) * (1f - phase)
            p.alpha = ((if (strong) 150 else 65) * sin(phase * PI).toFloat()).toInt().coerceIn(0, 255)
            ring.set(game.x - r, game.y - r, game.x + r, game.y + r)
            c.drawArc(ring, visualTime * 100f + i * 120f, if (strong) 220f else 140f, false, p)
        }
        p.alpha = 255; p.style = Paint.Style.FILL
    }

    private fun drawGoalMarker(c: Canvas) {
        val goal = game.goal
        if (!running || game.sceneCleared || game.finished || !goal.active || transition >= 0f) return
        val gx = width / 2f + (goal.x - cameraX) * zoom
        val gy = centerY + (goal.y - cameraY) * zoom
        val pad = 28f * density
        if (gx in pad..width - pad && gy in topInset + pad..height - bottomInset - pad) return
        val dx = gx - width / 2f; val dy = gy - centerY
        val factor = min((width / 2f - pad) / abs(dx).coerceAtLeast(1f),
            (playHeight / 2f - pad).coerceAtLeast(1f) / abs(dy).coerceAtLeast(1f)).coerceAtMost(1f)
        val xx = width / 2f + dx * factor; val yy = centerY + dy * factor
        p.color = 0xECFFF5D9.toInt(); c.drawCircle(xx, yy, 22f * density, p)
        p.color = GluttonArt.GOLD; p.style = Paint.Style.STROKE; p.strokeWidth = 2f * density
        c.drawCircle(xx, yy, 22f * density, p); p.style = Paint.Style.FILL
        art.item(c, goal.prop, xx, yy, 16f * density, 0f)
        c.save(); c.translate(xx, yy); c.rotate(atan2(dy, dx) * 180f / PI.toFloat())
        p.color = if (game.canEat(goal)) 0xFF389B85.toInt() else 0xFFAC8A5D.toInt()
        p.strokeWidth = 3f * density; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(25f * density, -4f * density, 30f * density, 0f, p)
        c.drawLine(30f * density, 0f, 25f * density, 4f * density, p); c.restore()
    }

    private fun drawMap(c: Canvas) {
        if (!running) return
        val size = 82f * density; val left = 15f * density; val top = height - size - 12f * density
        p.color = 0xDBFFF6E8.toInt(); c.drawRoundRect(left, top, left + size, top + size, 13f * density, 13f * density, p)
        val inset = 6f * density; val span = size - inset * 2
        val mapX = left + inset; val mapY = top + inset
        val w = game.scene.width; val h = game.scene.height
        for (item in game.items) {
            if (!item.active || item.prop.goal || item.prop.treasure) continue
            val edible = game.canEat(item)
            if (item.moves) {
                p.color = if (edible) GluttonArt.MINT else GluttonArt.CORAL
                c.drawCircle(mapX + item.x / w * span, mapY + item.y / h * span, 2.2f * density, p)
            } else if (edible && item.radius >= game.radius * .3f) {
                p.color = if (item.radius >= game.radius * GluttonGame.BIG_BITE) 0xAA3CBA97.toInt() else 0x5577B199
                c.drawCircle(mapX + item.x / w * span, mapY + item.y / h * span, 1.2f * density, p)
            }
        }
        for (item in game.items) if (item.active && (item.prop.goal || item.prop.treasure)) art.star(c,
            mapX + item.x / w * span, mapY + item.y / h * span, (if (item.prop.goal) 4.5f else 3f) * density, 0xFFDCA843.toInt())
        p.color = GluttonArt.INK
        c.drawCircle(mapX + game.x / w * span, mapY + game.y / h * span, 3.3f * density, p)
        p.color = Color.WHITE
        c.drawCircle(mapX + game.x / w * span, mapY + game.y / h * span, 1.4f * density, p)
    }

    private fun drawDirectionHint(c: Canvas, speed: Float) {
        val food = nearest ?: return
        if (!food.active || game.finished || speed > 60f) return
        val dx = food.x - game.x; val dy = food.y - game.y
        val distance = hypot(dx, dy)
        if (distance < game.radius * 6f) return
        val reach = game.radius * 1.9f + sin(visualTime * 5f) * game.radius * .15f
        c.save(); c.translate(game.x + dx / distance * reach, game.y + dy / distance * reach)
        c.rotate(atan2(dy, dx) * 180f / PI.toFloat()); c.scale(game.radius / 16f, game.radius / 16f)
        p.color = 0xBB3A987E.toInt(); p.strokeWidth = 4f; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(-7f, -6f, 1f, 0f, p); c.drawLine(1f, 0f, -7f, 6f, p); c.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!running || game.finished || game.sceneCleared || transition >= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y < topInset) return false
                pointer = event.getPointerId(0)
                originX = event.x; originY = event.y; fingerX = event.x; fingerY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0) { cancelInput(); return true }
                val dx = event.getX(index) - originX; val dy = event.getY(index) - originY
                val distance = hypot(dx, dy)
                val maxDrag = 42f * density
                // Floating joystick: follow a long drag, so changing direction remains effortless.
                if (distance > maxDrag * 1.7f) {
                    originX += dx / distance * (distance - maxDrag * 1.7f)
                    originY += dy / distance * (distance - maxDrag * 1.7f)
                }
                val nx = event.getX(index) - originX; val ny = event.getY(index) - originY
                val length = hypot(nx, ny); val ratio = maxDrag / max(maxDrag, length)
                fingerX = originX + nx * ratio; fingerY = originY + ny * ratio
                if (length > 5f * density) {
                    hasSteered = true; game.steer(nx / maxDrag, ny / maxDrag)
                } else game.steer(0f, 0f)
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) cancelInput()
            MotionEvent.ACTION_UP -> { cancelInput(); performClick() }
            MotionEvent.ACTION_CANCEL -> cancelInput()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); zoom = 0f; cameraX = game.x; cameraY = game.y; cancelInput()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); audio = GluttonAudio(context)
        atlas.prepare(game.scene)
        if (running) postOnAnimation(frame)
    }
    override fun onDetachedFromWindow() {
        setRunning(false); audio?.release(); audio = null; atlas.release(); super.onDetachedFromWindow()
    }

    companion object {
        const val TRANSITION_SWAP = .7f
        const val TRANSITION_TOTAL = 1.6f
    }
}
