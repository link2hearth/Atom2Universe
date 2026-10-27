package com.Atom2Universe.app.games.glutton

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Theme
import com.Atom2Universe.app.games.glutton.GluttonWorld.Kind
import com.Atom2Universe.app.games.glutton.GluttonWorld.Ring
import kotlin.math.*
import kotlin.random.Random

/** One hardware-accelerated View in metres: the camera zooms out as you grow, cells and walls are painted on the fly. */
class GluttonView(context: Context) : View(context) {
    var game = GluttonGame(); private set
    var onHudChanged: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onMilestone: ((Float) -> Unit)? = null
    var onRing: ((Theme) -> Unit)? = null
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

    fun start(seed: Long = Random.nextLong(), mode: GluttonGame.Mode = GluttonGame.Mode.CHRONO) {
        game = GluttonGame(seed, mode)
        particles.forEach { it.life = 0f }; bites.forEach { it.life = 0f }; labels.forEach { it.life = 0f }
        cameraX = game.x; cameraY = game.y; zoom = 0f
        pop = 0f; impact = 0f; visualTime = 0f
        hasSteered = false; nearest = null; nextHint = 0f
        finishDelay = -1f; shockwave = 0f; lastHud = -1f; lastHaptic = -1f; lastSound = -1f
        atlas.prepare(game.cell.ring.theme)
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
            game.update(dt)
            for (event in game.events) react(event)
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
            for (label in labels) if (label.life > 0f) { label.life -= dt; label.y -= dt * game.radius * .8f }
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

    private fun updateCamera(dt: Float) {
        if (width <= 0 || height <= 0) return
        val outer = game.world.rings.last()
        val target = max(width / (game.radius * 14f), max(width / outer.side, playHeight / outer.side))
        zoom = if (zoom <= 0f) target else zoom + (target - zoom) * (1f - exp(-dt * 2.4f))
        val halfW = width / (2 * zoom); val halfH = playHeight / (2 * zoom)
        cameraX = cameraX.coerceIn(-outer.half + halfW, max(-outer.half + halfW, outer.half - halfW))
        cameraY = cameraY.coerceIn(-outer.half + halfH, max(-outer.half + halfH, outer.half - halfH))
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
            GluttonGame.Effect.RING -> {
                val theme = game.world.rings[event.value].theme
                atlas.prepare(theme); emit(game.x, game.y, 30, GluttonArt.GOLD)
                if (sound) audio?.play(1, .85f)
                onRing?.invoke(theme)
            }
            GluttonGame.Effect.KNOCK -> {
                impact = 1f; pop = -.6f; haptic(HapticFeedbackConstants.LONG_PRESS)
                emit(game.x, game.y, 10, GluttonArt.CORAL)
                if (sound) audio?.play(3)
            }
            GluttonGame.Effect.FEVER -> { emit(game.x, game.y, 24, GluttonArt.GOLD); haptic(HapticFeedbackConstants.LONG_PRESS) }
            GluttonGame.Effect.BONUS -> { emit(event.x, event.y, 24, GluttonArt.GOLD); if (sound) audio?.play(1, 1.3f) }
            GluttonGame.Effect.FINISH -> { finishDelay = 0f; cancelInput(); haptic(HapticFeedbackConstants.LONG_PRESS) }
            GluttonGame.Effect.BURST -> Unit
        }
    }

    private fun emit(x: Float, y: Float, count: Int, color: Int) {
        val r = game.radius
        repeat(count) {
            val particle = particles[particleCursor++ % particles.size]
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = r * (3f + random.nextFloat() * 7f)
            particle.x = x; particle.y = y; particle.vx = cos(angle) * speed; particle.vy = sin(angle) * speed
            particle.total = .3f + random.nextFloat() * .4f; particle.life = particle.total
            particle.radius = r * (.1f + random.nextFloat() * .18f); particle.color = color
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
        val left = cameraX - width / (2 * zoom); val right = cameraX + width / (2 * zoom)
        val top = cameraY - playHeight / (2 * zoom); val bottom = cameraY + playHeight / (2 * zoom)
        drawWorld(c, left, top, right, bottom)
        val r = game.radius
        val margin = r * 2f
        val minimum = .6f / zoom
        for (pass in 0..1) for (item in game.items) {
            if (!item.active || item.moves != (pass == 1)) continue
            if (item.radius < minimum && !item.prop.bonus) continue
            if (item.x + item.radius < left - margin || item.x - item.radius > right + margin || item.y + item.radius < top - margin || item.y - item.radius > bottom + margin) continue
            val edible = game.canEat(item)
            if (item.prop.bonus) {
                p.color = 0x55FFD46F; c.drawCircle(item.x, item.y, item.radius * 1.3f, p)
                art.item(c, item.prop, item.x, item.y + sin(visualTime * 3f + item.id) * item.radius * .15f, item.radius, 0f)
                continue
            }
            // Only the really rewarding bites get a halo; the rest stays clean.
            if (edible && item.radius >= r * GluttonGame.BIG_BITE) {
                p.color = 0x4450BA99; c.drawCircle(item.x, item.y, item.radius * 1.25f, p)
                p.color = 0x997BE4AC.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = item.radius * .08f
                c.drawCircle(item.x, item.y, item.radius * 1.25f, p); p.style = Paint.Style.FILL
            }
            val alpha = if (item.cooldown > 0f) 150 + (sin(visualTime * 30f) * 60f).toInt() else 255
            art.item(c, item.prop, item.x, item.y, item.radius, if (item.moves) facing(item) else item.angle, alpha)
            if (item.moves) {
                if (item.stunned > 0f) for (i in 0..2) {
                    val a = visualTime * 3f + i * 2.1f
                    art.star(c, item.x + cos(a) * item.radius, item.y - item.radius + sin(a) * item.radius * .3f, item.radius * .25f, GluttonArt.GOLD)
                } else if (item.alert) art.alarm(c, item.x, item.y - item.radius * 1.1f, item.radius * .8f, !edible)
            }
        }
        drawSuction(c)
        if (shockwave > 0f) {
            p.color = GluttonArt.MINT; p.alpha = (shockwave * 220).toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = r * .12f
            c.drawCircle(game.x, game.y, r + (1f - shockwave) * (game.suctionRadius + r), p)
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
            for (i in 0..2) art.star(c, game.x + cos(visualTime * 5f + i * 2.1f) * (r * 1.4f),
                game.y - r * 1.2f + sin(visualTime * 5f + i * 2.1f) * r * .3f, r * .18f, GluttonArt.GOLD)
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

    /** Terraced cells, walls with their gaps, stairs and cliff shadows, outer rings first so inner rings paint over their centre cell. */
    private fun drawWorld(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val world = game.world
        for (index in world.rings.indices.reversed()) {
            val ring = world.rings[index]
            if (ring.half < left || -ring.half > right || ring.half < top || -ring.half > bottom) continue
            if (index == 0 && game.houseEaten) {
                // Only the bare plot is left where your house stood.
                p.color = LOT; c.drawRect(-ring.half, -ring.half, ring.half, ring.half, p); continue
            }
            val pixels = ring.cell * zoom
            val palette = PALETTES[ring.theme.ordinal]
            if (pixels < 3f) {
                p.color = palette[1]; c.drawRect(-ring.half, -ring.half, ring.half, ring.half, p); continue
            }
            val c0 = ((left + ring.half) / ring.cell).toInt().coerceIn(0, ring.count - 1)
            val c1 = ((right + ring.half) / ring.cell).toInt().coerceIn(0, ring.count - 1)
            val r0 = ((top + ring.half) / ring.cell).toInt().coerceIn(0, ring.count - 1)
            val r1 = ((bottom + ring.half) / ring.cell).toInt().coerceIn(0, ring.count - 1)
            for (row in r0..r1) for (col in c0..c1) {
                val cell = ring.cell(col, row)
                p.color = palette[cell.level.coerceAtMost(palette.size - 1)]
                c.drawRect(cell.x0, cell.y0, cell.x1, cell.y1, p)
                if (pixels > 40f) decorate(c, ring, cell)
            }
            // Cliffs: a soft shadow at the foot of every higher neighbour, a dark lip along its brink, stairs in the gaps.
            // Open ground between cells of one height draws nothing, so terraces read as broad shapes.
            val cliffColor = CLIFFS[ring.theme.ordinal]
            for (row in r0..r1) for (col in c0..c1) {
                val cell = ring.cell(col, row)
                for (side in 0..3) {
                    val other = world.neighbour(cell, side) ?: continue
                    if (other.height <= cell.height) continue
                    val band = ring.cell * .06f
                    p.color = 0x2E000000
                    when (side) {
                        0 -> c.drawRect(cell.x0, cell.y0, cell.x1, cell.y0 + band, p)
                        1 -> c.drawRect(cell.x1 - band, cell.y0, cell.x1, cell.y1, p)
                        2 -> c.drawRect(cell.x0, cell.y1 - band, cell.x1, cell.y1, p)
                        else -> c.drawRect(cell.x0, cell.y0, cell.x0 + band, cell.y1, p)
                    }
                    drawCliff(c, ring, cell, side, cliffColor)
                }
                if (col == 0) drawBoundary(c, ring, cell, 3)
                if (row == 0) drawBoundary(c, ring, cell, 0)
                if (col == ring.count - 1) drawBoundary(c, ring, cell, 1)
                if (row == ring.count - 1) drawBoundary(c, ring, cell, 2)
            }
            // The inner ring sits lower: a shadow around its square.
            world.rings.getOrNull(index - 1)?.let { inner ->
                val band = ring.cell * .05f
                p.color = 0x44000000
                c.drawRect(-inner.half - band, -inner.half - band, inner.half + band, -inner.half, p)
                c.drawRect(-inner.half - band, inner.half, inner.half + band, inner.half + band, p)
                c.drawRect(-inner.half - band, -inner.half, -inner.half, inner.half, p)
                c.drawRect(inner.half, -inner.half, inner.half + band, inner.half, p)
            }
        }
    }

    private fun decorate(c: Canvas, ring: Ring, cell: GluttonWorld.Cell) {
        val seed = cell.col * 31 + cell.row * 17 + ring.index * 101
        p.color = 0x16000000
        for (i in 0..5) {
            val fx = ((seed * (i + 3) * 2654435761L) % 1000) / 1000f; val fy = ((seed * (i + 7) * 40503L) % 1000) / 1000f
            val s = ring.cell * (.04f + i % 3 * .02f)
            if (ring.theme == Theme.ROOM) c.drawRect(cell.x0 + fx * ring.cell, cell.y0 + fy * ring.cell, cell.x0 + fx * ring.cell + s * 3, cell.y0 + fy * ring.cell + s * .4f, p)
            else c.drawCircle(cell.x0 + fx * ring.cell, cell.y0 + fy * ring.cell, s, p)
        }
    }

    /** The brink of a higher neighbour across [side] of [cell]: a dark lip, broken by its stairs. */
    private fun drawCliff(c: Canvas, ring: Ring, cell: GluttonWorld.Cell, side: Int, color: Int) {
        val edge = game.world.edge(cell, side) ?: return
        val horizontal = side == 0 || side == 2
        val line = when (side) { 0 -> cell.y0; 1 -> cell.x1; 2 -> cell.y1; else -> cell.x0 }
        val from = if (horizontal) cell.x0 else cell.y0
        val until = if (horizontal) cell.x1 else cell.y1
        val t = ring.cell * .012f
        if (edge.kind == Kind.CLIFF) { wall(c, horizontal, line, from, until, t, color); return }
        val centre = from + edge.offset * ring.cell
        wall(c, horizontal, line, from, centre - edge.width / 2f, t, color)
        wall(c, horizontal, line, centre + edge.width / 2f, until, t, color)
        stairs(c, horizontal, line, centre - edge.width / 2f, centre + edge.width / 2f, ring.cell * .16f)
    }

    /** The ring's boundary: the house has walls with its two doors, the suburb a brink up to the city, the city an end. */
    private fun drawBoundary(c: Canvas, ring: Ring, cell: GluttonWorld.Cell, side: Int) {
        val world = game.world
        val horizontal = side == 0 || side == 2
        val line = when (side) { 0 -> cell.y0; 1 -> cell.x1; 2 -> cell.y1; else -> cell.x0 }
        val from = if (horizontal) cell.x0 else cell.y0
        val until = if (horizontal) cell.x1 else cell.y1
        val color = WALLS[ring.theme.ordinal]
        val t = if (ring.index == 0) ring.thickness * 2f else ring.cell * .012f
        if (ring.index == world.rings.size - 1) { wall(c, horizontal, line, from, until, ring.cell * .02f, color); return }
        var gapFrom = 0f; var gapTo = 0f
        for ((exitSide, offset) in ring.exits) if (exitSide == side) {
            val centre = -ring.half + offset * ring.side; gapFrom = centre - ring.exitHalf; gapTo = centre + ring.exitHalf
        }
        if (gapTo > gapFrom && gapFrom < until && gapTo > from) {
            wall(c, horizontal, line, from, gapFrom, t, color); wall(c, horizontal, line, gapTo, until, t, color)
            stairs(c, horizontal, line, gapFrom, gapTo, ring.cell * .22f)
        } else wall(c, horizontal, line, from, until, t, color)
    }

    private fun wall(c: Canvas, horizontal: Boolean, line: Float, from: Float, until: Float, thickness: Float, color: Int) {
        if (until <= from) return
        p.color = color
        if (horizontal) c.drawRect(from, line - thickness, until, line + thickness, p) else c.drawRect(line - thickness, from, line + thickness, until, p)
    }

    private fun stairs(c: Canvas, horizontal: Boolean, line: Float, from: Float, until: Float, depth: Float) {
        p.color = 0xFFF5E6CF.toInt()
        if (horizontal) c.drawRect(from, line - depth / 2f, until, line + depth / 2f, p) else c.drawRect(line - depth / 2f, from, line + depth / 2f, until, p)
        p.color = 0x55564A5A
        for (i in 1..4) {
            val at = line - depth / 2f + depth * i / 5f
            if (horizontal) c.drawRect(from, at - depth * .03f, until, at + depth * .03f, p) else c.drawRect(at - depth * .03f, from, at + depth * .03f, until, p)
        }
    }

    private fun facing(item: GluttonGame.Item): Float {
        if (abs(item.vx) + abs(item.vy) < item.radius * .5f) return item.angle
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
        p.style = Paint.Style.STROKE; p.strokeWidth = game.radius * if (strong) .08f else .04f
        for (i in 0 until if (strong) 3 else 1) {
            val phase = (visualTime * 1.6f + i / 3f) % 1f
            val r = game.radius + (game.suctionRadius - game.radius) * (1f - phase)
            p.alpha = ((if (strong) 150 else 65) * sin(phase * PI).toFloat()).toInt().coerceIn(0, 255)
            ring.set(game.x - r, game.y - r, game.x + r, game.y + r)
            c.drawArc(ring, visualTime * 100f + i * 120f, if (strong) 220f else 140f, false, p)
        }
        p.alpha = 255; p.style = Paint.Style.FILL
    }

    /** Map of the ring you are in: terraces, hourglasses and you. */
    private fun drawMap(c: Canvas) {
        if (!running) return
        val size = 82f * density; val left = 15f * density; val top = height - size - 12f * density
        p.color = 0xDBFFF6E8.toInt(); c.drawRoundRect(left, top, left + size, top + size, 13f * density, 13f * density, p)
        val inset = 6f * density; val span = size - inset * 2
        val mapX = left + inset; val mapY = top + inset
        val ring = game.cell.ring
        val palette = PALETTES[ring.theme.ordinal]
        val cellPx = span / ring.count
        for (cell in ring.cells) {
            p.color = palette[cell.level.coerceAtMost(palette.size - 1)]
            c.drawRect(mapX + cell.col * cellPx, mapY + cell.row * cellPx, mapX + (cell.col + 1) * cellPx - .5f * density, mapY + (cell.row + 1) * cellPx - .5f * density, p)
        }
        fun mx(x: Float) = mapX + (x + ring.half) / ring.side * span
        fun my(y: Float) = mapY + (y + ring.half) / ring.side * span
        for (item in game.items) if (item.active && item.prop.bonus && item.cell?.ring === ring) {
            p.color = GluttonArt.GOLD; c.drawCircle(mx(item.x), my(item.y), 1.8f * density, p)
        }
        for (item in game.items) if (item.active && item.moves && item.cell?.ring === ring) {
            p.color = if (game.canEat(item)) GluttonArt.MINT else GluttonArt.CORAL
            c.drawCircle(mx(item.x), my(item.y), 1.4f * density, p)
        }
        p.color = GluttonArt.INK
        c.drawCircle(mx(game.x), my(game.y), 3.3f * density, p)
        p.color = Color.WHITE
        c.drawCircle(mx(game.x), my(game.y), 1.4f * density, p)
    }

    private fun drawDirectionHint(c: Canvas, speed: Float) {
        val food = nearest ?: return
        if (!food.active || game.finished || speed > game.moveSpeed * .4f) return
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
        if (!running || game.finished) return false
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
        atlas.prepare(game.cell.ring.theme)
        if (running) postOnAnimation(frame)
    }
    override fun onDetachedFromWindow() {
        setRunning(false); audio?.release(); audio = null; atlas.release(); super.onDetachedFromWindow()
    }

    companion object {
        /** Floor colour per terrace level, lighter as you climb: parquet, lawn, pavement. */
        val PALETTES = arrayOf(
            intArrayOf(0xFFE3C9B4.toInt(), 0xFFEDD8C5.toInt(), 0xFFF5E6D6.toInt()),
            intArrayOf(0xFFA9CC8E.toInt(), 0xFFB9D7A0.toInt(), 0xFFCBE3B4.toInt()),
            intArrayOf(0xFFC9CCC7.toInt(), 0xFFD5D8D2.toInt(), 0xFFE0E3DC.toInt(), 0xFFE9EBE5.toInt(), 0xFFF2F3EE.toInt()))
        /** House walls, the suburb's brink up to the city, the city's end. */
        val WALLS = intArrayOf(0xFF8C83A7.toInt(), 0xFF6F9A66.toInt(), 0xFF7E858F.toInt())
        /** Brink of a terrace, per theme: a darker tone of its floor. */
        val CLIFFS = intArrayOf(0xFFB8927A.toInt(), 0xFF6C9A5E.toInt(), 0xFF8E938C.toInt())
        const val LOT = 0xFFB9A98A.toInt()
    }
}
