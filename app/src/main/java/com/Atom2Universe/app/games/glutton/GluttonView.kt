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
import com.Atom2Universe.app.games.glutton.GluttonWorld.Ground
import com.Atom2Universe.app.games.glutton.GluttonWorld.Ring
import kotlin.math.*
import kotlin.random.Random

/**
 * One hardware-accelerated View. The world is in metres (doubles) and spans the whole universe, so every
 * position is turned into screen pixels here rather than through the canvas matrix: the camera zooms
 * out as you grow and only what is a pixel or more across is drawn.
 */
class GluttonView(context: Context) : View(context) {
    var game = GluttonGame(); private set
    var onHudChanged: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    /** Power of ten of the diameter just reached (−1 = 10 cm … 25). */
    var onMilestone: ((Int) -> Unit)? = null
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
    private val texture = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GluttonArt.INK; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val arc = RectF()
    private val oval = RectF()
    private val matrix = Matrix()
    /** One shader per ground texture and per version of it (see [GluttonAtlas]). */
    private val shaders = Array(Ground.entries.size) { arrayOfNulls<BitmapShader>(8) }
    private val flat = Paint()
    private val averages = IntArray(Ground.entries.size)
    private val random = Random(71)
    private class Particle {
        var x = 0.0; var y = 0.0; var vx = 0.0; var vy = 0.0
        var life = 0f; var total = 1f; var radius = 0.0; var color = 0
    }
    private class Gulp { var x = 0.0; var y = 0.0; var life = 0f; var prop: Prop? = null; var radius = 0.0; var angle = 0f; var flip = false }
    private class Label { var text = ""; var x = 0.0; var y = 0.0; var life = 0f; var scale = 1f }
    /** A spark of the Big Bang, in screen pixels. */
    private class Spark { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var total = 1f; var size = 1f; var color = 0 }
    private val particles = Array(128) { Particle() }
    private val gulps = Array(28) { Gulp() }
    private val labels = Array(4) { Label() }
    private val sparks = Array(220) { Spark() }
    /** Stars of the new universe: x, y (share of the screen), size (dp), twinkle phase. */
    private val stars = FloatArray(STARS * 4)
    private val bangPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bangMatrix = Matrix()
    private val fireball by lazy {
        RadialGradient(0f, 0f, 1f, intArrayOf(NEW_SKY, NEW_SKY, 0xFF3B2A6E.toInt(), CORAL_HOT, GluttonArt.GOLD, Color.WHITE),
            floatArrayOf(0f, .55f, .74f, .87f, .95f, 1f), Shader.TileMode.CLAMP)
    }
    private val glow by lazy { RadialGradient(0f, 0f, 1f, intArrayOf(Color.WHITE, 0xCCFFE9A8.toInt(), 0x00FFD46F), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP) }
    private val nebulae by lazy {
        intArrayOf(0x5555C7AF, 0x55B07CE8, 0x55F18488, 0x456FA8FF).map { RadialGradient(0f, 0f, 1f, it, it and 0x00FFFFFF, Shader.TileMode.CLAMP) }
    }
    /** How white space has turned while the last ring is eaten clean (0 to 1). */
    private var dawn = 0f
    /** Seconds since the last crumb of the universe went down (-1 = not yet): implosion, flash, Big Bang. */
    private var bang = -1f
    private val visible = ArrayList<GluttonGame.Item>(512)
    private var particleCursor = 0
    private var gulpCursor = 0
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
    /** Pixels per metre. */
    private var zoom = 0.0
    private var drift = 0.0
    private var driftY = 0.0
    private var pop = 0f
    private var impact = 0f
    private var visualTime = 0f
    private var hasSteered = false
    private var nearest: GluttonGame.Item? = null
    private var nextHint = 0f
    private var map: Bitmap? = null
    private val foodSpots = DoubleArray(MAP_DOTS * 2)
    private var foodDots = 0
    private var nextFoodMap = 0f
    private var mapRing = -1
    private val hintText = context.getString(R.string.glutton_drag)
    val isRunning get() = running
    val hasInput get() = pointer != -1
    private val density get() = resources.displayMetrics.density
    private val playHeight get() = (height - topInset - bottomInset).coerceAtLeast(1f)
    private val centerY get() = topInset + playHeight / 2
    private fun sx(x: Double) = ((x - cameraX) * zoom + width / 2.0).toFloat()
    private fun sy(y: Double) = ((y - cameraY) * zoom + centerY).toFloat()

    init {
        isClickable = true
        contentDescription = context.getString(R.string.glutton_touch)
    }

    fun setViewportInsets(top: Int, bottom: Int) {
        if (topInset == top.toFloat() && bottomInset == bottom.toFloat()) return
        topInset = top.toFloat(); bottomInset = bottom.toFloat(); cancelInput(); zoom = 0.0
        cameraX = game.x; cameraY = game.y; invalidate()
    }

    fun start(seed: Long = Random.nextLong(), mode: GluttonGame.Mode = GluttonGame.Mode.VOYAGE) {
        game = GluttonGame(seed, mode)
        particles.forEach { it.life = 0f }; gulps.forEach { it.life = 0f }; labels.forEach { it.life = 0f }; sparks.forEach { it.life = 0f }
        dawn = 0f; bang = -1f
        cameraX = game.x; cameraY = game.y; zoom = 0.0
        pop = 0f; impact = 0f; visualTime = 0f
        hasSteered = false; nearest = null; nextHint = 0f; mapRing = -1; foodDots = 0; nextFoodMap = 0f
        finishDelay = -1f; shockwave = 0f; lastHud = -1f; lastHaptic = -1f; lastSound = -1f
        atlas.prepare(game.ring.theme)
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

    fun cancelInput() { pointer = -1; game.steer(0.0, 0.0) }

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
                particle.vx *= (1.0 - dt * 2.0); particle.vy *= (1.0 - dt * 2.0)
            }
            for (gulp in gulps) if (gulp.life > 0f) {
                gulp.life -= dt
                val lerp = 1.0 - exp(-dt * 17.0)
                gulp.x += (game.x - gulp.x) * lerp; gulp.y += (game.y - gulp.y) * lerp
            }
            for (label in labels) if (label.life > 0f) { label.life -= dt; label.y -= dt * game.radius * .8 }
            val easing = 1.0 - exp(-dt * 7.0)
            cameraX += (game.x + game.vx * .12 - cameraX) * easing
            cameraY += (game.y + game.vy * .12 - cameraY) * easing
            drift -= game.vx * zoom * dt * .25; driftY -= game.vy * zoom * dt * .25
            updateCamera(dt)
            atlas.prepare(game.ring.theme)
            if (game.elapsed >= nextHint) { nearest = game.nearestFood(); nextHint = game.elapsed + .3f }
            if (game.elapsed - lastHud > .1f || game.finished) { lastHud = game.elapsed; onHudChanged?.invoke() }
            val white = if (game.collapsing || game.cleared && game.mode == GluttonGame.Mode.VOYAGE) 1f else game.finaleProgress
            dawn += (white - dawn) * (1f - exp(-dt * 2.5f))
            if (finishDelay >= 0f) {
                finishDelay += dt
                if (finishDelay >= .9f) { setRunning(false); onFinished?.invoke(); return }
            }
            if (bang >= 0f) {
                updateBang(dt)
                if (bang >= BANG_END) { setRunning(false); onFinished?.invoke(); return }
            }
            invalidate(); postOnAnimation(this)
        }
    }

    private fun updateCamera(dt: Float) {
        if (width <= 0 || height <= 0) return
        val target = min(width.toFloat(), playHeight) / (game.radius * VIEW_SPAN)
        zoom = if (zoom <= 0.0) target else exp(ln(zoom) + (ln(target) - ln(zoom)) * (1.0 - exp(-dt * 2.4)))
    }

    private fun react(event: GluttonGame.Event) {
        when (event.effect) {
            GluttonGame.Effect.EAT -> {
                val item = event.item ?: return
                val big = game.isBigBite(item)
                pop = if (big) 1.1f else .6f
                val gulp = gulps[gulpCursor++ % gulps.size]
                gulp.prop = item.prop; gulp.x = event.x; gulp.y = event.y; gulp.life = .24f
                gulp.radius = item.radius; gulp.angle = item.angle; gulp.flip = item.flip
                emit(event.x, event.y, if (big) 12 else 3, GluttonArt.MINT)
                if (big || item.cover) {
                    val label = labels[labelCursor++ % labels.size]
                    label.text = nameOf?.invoke(item.prop) ?: ""; label.x = event.x; label.y = event.y - item.radius
                    label.life = 1.4f; label.scale = (item.radius / game.radius).toFloat().coerceIn(.7f, 1.3f)
                }
                if (sound && game.elapsed - lastSound > .07f) {
                    lastSound = game.elapsed; audio?.play(0, (if (big) .7f else .9f) + min(game.combo, 30) * .01f)
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
                onMilestone?.invoke(event.value)
            }
            GluttonGame.Effect.RING -> {
                val rings = game.world.rings
                val theme = rings[event.value].theme
                atlas.prepare(theme)
                if (event.value == 0 || rings[event.value - 1].theme != theme) {
                    emit(game.x, game.y, 30, GluttonArt.GOLD)
                    if (sound) audio?.play(1, .85f)
                    onRing?.invoke(theme)
                }
            }
            GluttonGame.Effect.KNOCK -> {
                impact = 1f; pop = -.6f; haptic(HapticFeedbackConstants.LONG_PRESS)
                emit(game.x, game.y, 10, GluttonArt.CORAL)
                if (sound) audio?.play(3)
            }
            GluttonGame.Effect.BONUS -> { emit(event.x, event.y, 24, GluttonArt.GOLD); if (sound) audio?.play(1, 1.3f) }
            GluttonGame.Effect.COLLAPSE -> {
                impact = .8f; pop = 1.5f; emit(game.x, game.y, 40, GluttonArt.GOLD)
                haptic(HapticFeedbackConstants.LONG_PRESS)
                if (sound) audio?.play(2, .6f)
            }
            GluttonGame.Effect.FINISH -> {
                cancelInput()
                if (game.cleared) { bang = 0f; if (sound) audio?.play(2, .5f) }
                else { finishDelay = 0f; haptic(HapticFeedbackConstants.LONG_PRESS) }
            }
            GluttonGame.Effect.BURST -> Unit
        }
    }

    private fun emit(x: Double, y: Double, count: Int, color: Int) {
        val r = game.radius
        repeat(count) {
            val particle = particles[particleCursor++ % particles.size]
            val angle = random.nextDouble() * 2 * PI
            val speed = r * (3 + random.nextDouble() * 7)
            particle.x = x; particle.y = y; particle.vx = cos(angle) * speed; particle.vy = sin(angle) * speed
            particle.total = .3f + random.nextFloat() * .4f; particle.life = particle.total
            particle.radius = r * (.1 + random.nextDouble() * .18); particle.color = color
        }
    }

    private fun haptic(type: Int) { if (haptics) performHapticFeedback(type) }

    /** The gobbler shrinks to a point, ticking ever faster, then bursts into a new universe. */
    private fun updateBang(dt: Float) {
        val before = bang
        bang += dt
        if (bang < BANG_AT) {
            val f = bang / BANG_AT
            impact = max(impact, f * f * 1.2f)
            // Haptic ticks closer and closer together as the universe crushes into one point.
            val gap = .16f - f * .12f
            if (floor(bang / gap) != floor(before / gap)) haptic(HapticFeedbackConstants.CLOCK_TICK)
        } else if (before < BANG_AT) explode()
        for (spark in sparks) if (spark.life > 0f) {
            spark.life -= dt; spark.x += spark.vx * dt; spark.y += spark.vy * dt
            val drag = 1f - dt * .9f; spark.vx *= drag; spark.vy *= drag
        }
    }

    private fun explode() {
        impact = 1.6f; haptic(HapticFeedbackConstants.LONG_PRESS)
        if (sound) { audio?.play(3, .5f); audio?.play(1, .55f) }
        val cx = width / 2f; val cy = centerY
        for (spark in sparks) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = (220f + random.nextFloat().pow(2) * 1500f) * density
            spark.x = cx; spark.y = cy; spark.vx = cos(angle) * speed; spark.vy = sin(angle) * speed
            spark.total = 1.2f + random.nextFloat() * 1.6f; spark.life = spark.total
            spark.size = (1.2f + random.nextFloat() * 3.4f) * density
            spark.color = SPARKS[random.nextInt(SPARKS.size)]
        }
        for (i in 0 until STARS) {
            stars[i * 4] = random.nextFloat(); stars[i * 4 + 1] = random.nextFloat()
            stars[i * 4 + 2] = .6f + random.nextFloat().pow(3) * 1.8f; stars[i * 4 + 3] = random.nextFloat() * 6.3f
        }
    }

    /** The new universe racing out from where you were: a hot front, cooling into a starry sky behind it. */
    private fun drawBigBang(c: Canvas) {
        val t = bang - BANG_AT
        val cx = width / 2f; val cy = centerY
        val far = hypot(max(cx, width - cx), max(cy, height - cy))
        val grow = (t / FIREBALL).coerceIn(0f, 1f)
        c.save()
        if (impact > 0f) c.translate(sin(visualTime * 85f) * impact * 7f * density, cos(visualTime * 71f) * impact * 4f * density)
        val front = far * (1.2f * (1f - (1f - grow).pow(3)) + max(0f, t - FIREBALL) * 1.4f)
        if (front * .55f >= far) c.drawColor(NEW_SKY)
        else if (front > 0f) {
            bangMatrix.setScale(front, front); bangMatrix.postTranslate(cx, cy)
            fireball.setLocalMatrix(bangMatrix); bangPaint.shader = fireball; bangPaint.alpha = 255
            c.drawCircle(cx, cy, front, bangPaint)
        }
        // Clouds of new galaxies, fading in behind the front.
        val clouds = ((t - .4f) / 1.4f).coerceIn(0f, 1f)
        if (clouds > 0f) for ((i, shader) in nebulae.withIndex()) {
            val a = i * 1.7f + .6f; val d = far * (.18f + i * .11f) * (.6f + .4f * clouds)
            val radius = far * (.32f + (i % 2) * .14f) * (.7f + .3f * clouds)
            val nx = cx + cos(a) * d; val ny = cy + sin(a) * d * .8f
            bangMatrix.setScale(radius, radius); bangMatrix.postTranslate(nx, ny)
            shader.setLocalMatrix(bangMatrix); bangPaint.shader = shader; bangPaint.alpha = (255 * clouds).toInt()
            c.drawCircle(nx, ny, radius, bangPaint)
        }
        bangPaint.shader = null
        val shine = ((t - .3f) / 1.1f).coerceIn(0f, 1f)
        if (shine > 0f) for (i in 0 until STARS) {
            val x = stars[i * 4] * width; val y = stars[i * 4 + 1] * height
            if (hypot(x - cx, y - cy) > front * .8f) continue
            val twinkle = .55f + .45f * sin(visualTime * 3f + stars[i * 4 + 3])
            bangPaint.color = Color.WHITE; bangPaint.alpha = (255 * shine * twinkle).toInt()
            c.drawCircle(x, y, stars[i * 4 + 2] * density, bangPaint)
        }
        // What is left of the blast at the centre.
        val heat = (1f - t / 1.3f).coerceIn(0f, 1f)
        if (heat > 0f) {
            val radius = far * (.15f + .35f * grow)
            bangMatrix.setScale(radius, radius); bangMatrix.postTranslate(cx, cy)
            glow.setLocalMatrix(bangMatrix); bangPaint.shader = glow; bangPaint.alpha = (255 * heat).toInt()
            c.drawCircle(cx, cy, radius, bangPaint); bangPaint.shader = null
        }
        for (spark in sparks) if (spark.life > 0f) {
            bangPaint.color = spark.color; bangPaint.alpha = (255 * (spark.life / spark.total).coerceIn(0f, 1f)).toInt()
            c.drawCircle(spark.x, spark.y, spark.size, bangPaint)
        }
        // The flash: everything blown out to white for an instant.
        val flash = (1f - t / .4f).coerceIn(0f, 1f)
        c.restore()
        if (flash > 0f) { bangPaint.color = FLASH; bangPaint.alpha = (255 * flash * flash).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bangPaint) }
        bangPaint.alpha = 255
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val space = game.ring.theme.ordinal >= Theme.PLANETS.ordinal
        c.drawColor(if (space) 0xFF141A33.toInt() else 0xFFEEDACE.toInt())
        if (width == 0 || height == 0) return
        if (zoom <= 0.0) updateCamera(0f)
        c.save(); c.clipRect(0f, topInset, width.toFloat(), height - bottomInset)
        if (impact > 0f) c.translate(sin(visualTime * 85f) * impact * 5f * density, 0f)
        val halfW = width / (2 * zoom); val halfH = playHeight / (2 * zoom)
        val left = cameraX - halfW; val right = cameraX + halfW; val top = cameraY - halfH; val bottom = cameraY + halfH
        drawWorld(c, left, top, right, bottom)
        if (dawn > .002f) {
            p.color = DAWN; p.alpha = (255 * dawn).toInt().coerceIn(0, 255)
            val pad = 12f * density
            c.drawRect(-pad, topInset - pad, width + pad, height - bottomInset + pad, p); p.alpha = 255
        }
        val r = game.radius
        val px = (r * zoom).toFloat()
        drawItems(c, left, top, right, bottom)
        drawSuction(c)
        val gx = sx(game.x); val gy = sy(game.y)
        if (shockwave > 0f) {
            p.color = GluttonArt.MINT; p.alpha = (shockwave * 220).toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = px * .12f
            c.drawCircle(gx, gy, px + (1f - shockwave) * ((game.suctionRadius + r) * zoom).toFloat(), p)
            p.alpha = 255; p.style = Paint.Style.FILL
        }
        for (gulp in gulps) if (gulp.life > 0f) gulp.prop?.let {
            val f = (gulp.life / .24f).coerceIn(0f, 1f)
            art.item(c, it, sx(gulp.x), sy(gulp.y), (gulp.radius * zoom).toFloat() * f, gulp.angle + (1f - f) * 150f, flip = gulp.flip)
        }
        if (bang < 0f) art.body(c, gx, gy, px, visualTime, (game.vx / game.moveSpeed).toFloat(), (game.vy / game.moveSpeed).toFloat(),
            pop, game.burst > 0f || pop > .2f, impact > .2f, false)
        else if (bang < BANG_AT) {
            // Crushing down to a single point, slowly and then all at once.
            val f = bang / BANG_AT
            val size = px * (1f - f * f * f)
            if (size > 1f) art.body(c, gx, gy, size, visualTime, 0f, 0f, 1f, true, false, false)
            val core = 3f * density * (1f + f * 1.5f)
            p.color = GluttonArt.GOLD; p.alpha = (110 * f + 40 * sin(visualTime * 40f) * f).toInt().coerceIn(0, 255)
            c.drawCircle(gx, gy, core * 3f, p)
            p.color = SINGULARITY; p.alpha = (255 * min(1f, f * 2f)).toInt(); c.drawCircle(gx, gy, core, p); p.alpha = 255
        }
        if (game.invulnerable > 0f && impact > 0f) {
            for (i in 0..2) art.star(c, gx + cos(visualTime * 5f + i * 2.1f) * px * 1.4f,
                gy - px * 1.2f + sin(visualTime * 5f + i * 2.1f) * px * .3f, px * .18f, GluttonArt.GOLD)
        }
        for (particle in particles) if (particle.life > 0f) {
            p.color = particle.color; p.alpha = (255 * particle.life / particle.total).toInt().coerceIn(0, 255)
            c.drawCircle(sx(particle.x), sy(particle.y), (particle.radius * zoom).toFloat(), p)
        }
        p.alpha = 255
        for (label in labels) if (label.life > 0f && label.text.isNotEmpty()) {
            val alpha = (255 * (label.life / 1.4f).coerceIn(0f, 1f)).toInt()
            textPaint.textSize = 17f * density * label.scale
            textPaint.color = Color.WHITE; textPaint.alpha = alpha
            textPaint.style = Paint.Style.STROKE; textPaint.strokeWidth = 4f * density
            c.drawText(label.text, sx(label.x), sy(label.y), textPaint)
            textPaint.style = Paint.Style.FILL; textPaint.color = GluttonArt.INK; textPaint.alpha = alpha
            c.drawText(label.text, sx(label.x), sy(label.y), textPaint); textPaint.alpha = 255
        }
        drawDirectionHint(c)
        c.restore()
        if (bang >= BANG_AT) { drawBigBang(c); return }
        if (bang < 0f) drawMap(c)
        if (!hasSteered && running && game.elapsed < 7f) {
            textPaint.textSize = 14f * density; textPaint.color = if (space) Color.WHITE else GluttonArt.INK
            c.drawText(hintText, width / 2f, centerY + px + 50f * density, textPaint)
        }
        if (pointer != -1) {
            p.color = 0x33405161; c.drawCircle(originX, originY, 42f * density, p)
            p.color = 0x88FFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * density
            c.drawCircle(originX, originY, 42f * density, p); p.style = Paint.Style.FILL
            p.color = 0xAA55C7AF.toInt(); c.drawCircle(fingerX, fingerY, 18f * density, p)
        }
    }

    /** Rings from the outside in, so each inner ring paints over the middle of the one around it. */
    private fun drawWorld(c: Canvas, left: Double, top: Double, right: Double, bottom: Double) {
        val world = game.world
        val house = world.rings[0]
        for (index in world.rings.indices.reversed()) {
            val ring = world.rings[index]
            if (!ring.built) continue
            if (ring.half < left || -ring.half > right || ring.half < top || -ring.half > bottom) continue
            if (ring.half * zoom < 1.5) break
            if (game.covered[index]) {
                // Only the bare plot is left where your house stood (until the Earth itself goes).
                if (index == 0 && game.houseEaten && !game.covered[5]) { p.color = LOT; rect(c, -house.half, -house.half, house.half, house.half, p) }
                continue
            }
            if (ring.round) band(c, ring, ring.ground, ring.half / 2, ring.half, left, top, right, bottom)
            else groundRect(c, ring, ring.ground, -ring.half, -ring.half, ring.half, ring.half, false)
            for (patch in ring.patches) {
                if (patch.x1 < left || patch.x0 > right || patch.y1 < top || patch.y0 > bottom) continue
                if ((patch.x1 - patch.x0) * zoom < 1.0 && (patch.y1 - patch.y0) * zoom < 1.0) continue
                groundRect(c, ring, patch.ground, patch.x0, patch.y0, patch.x1, patch.y1, patch.round)
            }
            for (annulus in ring.annuli) if (annulus.width * zoom >= .7) band(c, ring, annulus.ground, annulus.radius, annulus.width, left, top, right, bottom)
            for (m in ring.markings) {
                val w = m.width * zoom
                if (w < .7) continue
                if (max(m.x0, m.x1) < left || min(m.x0, m.x1) > right || max(m.y0, m.y1) < top || min(m.y0, m.y1) > bottom) continue
                dashes(c, m, left, top, right, bottom, w.toFloat())
            }
            if (ring.circles.isNotEmpty()) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * density; p.color = 0x55B9C8FF
                for (circle in ring.circles) {
                    val rad = circle[2] * zoom
                    if (rad < 3 || rad > 2e5) continue
                    c.drawCircle(sx(circle[0]), sy(circle[1]), rad.toFloat(), p)
                }
                p.style = Paint.Style.FILL
            }
            if (index == 0 && !game.covered[0]) {
                p.color = WALL
                for (wall in ring.walls) {
                    if (wall.horizontal) rect(c, wall.from, wall.line - wall.thickness, wall.until, wall.line + wall.thickness, p)
                    else rect(c, wall.line - wall.thickness, wall.from, wall.line + wall.thickness, wall.until, p)
                }
            }
            // The ring inside sits a little lower: a soft shadow around its edge.
            world.rings.getOrNull(index - 1)?.let { inner ->
                if (game.covered[inner.index] && !(inner.index == 0 && game.houseEaten)) return@let
                val band = inner.half * .03
                p.color = 0x33000000
                if (inner.round) { circleBand(c, p, inner.half + band / 2, band, left, top, right, bottom); return@let }
                rect(c, -inner.half - band, -inner.half - band, inner.half + band, -inner.half, p)
                rect(c, -inner.half - band, inner.half, inner.half + band, inner.half + band, p)
                rect(c, -inner.half - band, -inner.half, -inner.half, inner.half, p)
                rect(c, inner.half, -inner.half, inner.half + band, inner.half, p)
            }
        }
    }

    /** A round band of ground around the centre (a whole round ring is the band from 0 to its edge), with its texture. */
    private fun band(c: Canvas, ring: Ring, ground: Ground, radius: Double, width: Double, left: Double, top: Double, right: Double, bottom: Double) {
        val paint = groundPaint(ring, ground)
        if (paint.shader != null && paint.alpha < 255) circleBand(c, flat, radius, width, left, top, right, bottom)
        circleBand(c, paint, radius, width, left, top, right, bottom)
    }

    /**
     * The ring between radii [radius] ± [width] / 2 around the centre. Seen from close, such a circle is many
     * screens wide: when the screen lies wholly inside the band it is simply filled, and when it misses it nothing is drawn.
     */
    private fun circleBand(c: Canvas, paint: Paint, radius: Double, width: Double, left: Double, top: Double, right: Double, bottom: Double) {
        val outer = radius + width / 2; val inner = radius - width / 2
        val near = hypot(max(0.0, max(left, -right)), max(0.0, max(top, -bottom)))
        val far = hypot(max(abs(left), abs(right)), max(abs(top), abs(bottom)))
        if (near > outer || far < inner) return
        if (far <= outer && near >= inner) { rect(c, left, top, right, bottom, paint); return }
        val style = paint.style; val stroke = paint.strokeWidth
        if (inner <= 0.0) { paint.style = Paint.Style.FILL; c.drawCircle(sx(0.0), sy(0.0), (outer * zoom).toFloat(), paint) }
        else { paint.style = Paint.Style.STROKE; paint.strokeWidth = (width * zoom).toFloat(); c.drawCircle(sx(0.0), sy(0.0), (radius * zoom).toFloat(), paint) }
        paint.style = style; paint.strokeWidth = stroke
    }

    /** A rectangle in metres, clipped to the screen before turning into pixels (they can be astronomically large). */
    private fun rect(c: Canvas, x0: Double, y0: Double, x1: Double, y1: Double, paint: Paint) {
        val l = max(sx(x0), -8f); val t = max(sy(y0), -8f); val r = min(sx(x1), width + 8f); val b = min(sy(y1), height + 8f)
        if (r > l && b > t) c.drawRect(l, t, r, b, paint)
    }

    /** Ground with its texture: world-anchored on land (so floors shrink as you grow), drifting behind you in space. */
    private fun groundRect(c: Canvas, ring: Ring, ground: Ground, x0: Double, y0: Double, x1: Double, y1: Double, round: Boolean) {
        val paint = groundPaint(ring, ground)
        // The texture fades into its average colour as it gets too fine to see, rather than switching all at once.
        if (paint.shader != null && paint.alpha < 255) shape(c, x0, y0, x1, y1, round, flat)
        shape(c, x0, y0, x1, y1, round, paint)
    }

    private fun shape(c: Canvas, x0: Double, y0: Double, x1: Double, y1: Double, round: Boolean, paint: Paint) {
        if (round) {
            val l = sx(x0); val t = sy(y0); val r = sx(x1); val b = sy(y1)
            if (r - l < 4e5f) { oval.set(l, t, r, b); c.drawOval(oval, paint); return }
        }
        rect(c, x0, y0, x1, y1, paint)
    }

    private fun groundPaint(ring: Ring, ground: Ground): Paint {
        val versions = atlas.ground(ground)
        val space = ground == Ground.SPACE
        texture.colorFilter = if (space) TINTS[ring.theme.ordinal] else null
        if (versions == null) { texture.shader = null; texture.color = FALLBACK[ground.ordinal]; return texture }
        if (averages[ground.ordinal] == 0) averages[ground.ordinal] = Bitmap.createScaledBitmap(versions.last(), 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()
        val tile = if (space) 360.0 * density else tileMetres(ring) * zoom
        if (tile < FADE_FROM) { texture.shader = null; texture.color = averages[ground.ordinal]; return texture }
        flat.color = averages[ground.ordinal]; flat.colorFilter = texture.colorFilter
        val level = atlas.groundLevel(versions, tile.toFloat())
        val bitmap = versions[level]
        val shader = shaders[ground.ordinal][level] ?: BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.MIRROR).also { shaders[ground.ordinal][level] = it }
        val ox = if (space) drift else -cameraX * zoom + width / 2.0
        val oy = if (space) driftY else -cameraY * zoom + centerY
        matrix.setScale((tile / bitmap.width).toFloat(), (tile / bitmap.height).toFloat())
        matrix.postTranslate(((ox % tile + tile) % tile).toFloat(), ((oy % tile + tile) % tile).toFloat())
        shader.setLocalMatrix(matrix)
        texture.shader = shader; texture.color = Color.WHITE
        texture.alpha = (255 * ((tile - FADE_FROM) / (FADE_TO - FADE_FROM))).toInt().coerceIn(0, 255)
        return texture
    }

    private fun tileMetres(ring: Ring) = if (ring.index == 0) 1.3 else ring.from * 8

    private fun dashes(c: Canvas, m: GluttonWorld.Marking, left: Double, top: Double, right: Double, bottom: Double, w: Float) {
        p.color = 0xCCFFF8E6.toInt(); p.strokeWidth = w; p.strokeCap = Paint.Cap.BUTT
        val horizontal = abs(m.y1 - m.y0) < abs(m.x1 - m.x0)
        val dash = m.width * 5
        val from = if (horizontal) max(min(m.x0, m.x1), left) else max(min(m.y0, m.y1), top)
        val until = if (horizontal) min(max(m.x0, m.x1), right) else min(max(m.y0, m.y1), bottom)
        var s = floor(from / (dash * 2)) * dash * 2
        var count = 0
        while (s < until && count++ < 200) {
            val a = max(s, from); val b = min(s + dash, until)
            if (b > a) {
                if (horizontal) c.drawLine(sx(a), sy(m.y0), sx(b), sy(m.y0), p) else c.drawLine(sx(m.x0), sy(a), sx(m.x0), sy(b), p)
            }
            s += dash * 2
        }
    }

    /** Everything a pixel or more across, covers first, then from the back to the front of the scene. */
    private fun drawItems(c: Canvas, left: Double, top: Double, right: Double, bottom: Double) {
        val r = game.radius
        val margin = r * 2
        visible.clear()
        val minimum = .6 / zoom
        val stamp = game.nextStamp()
        for (layer in game.layers) {
            if (layer == null || layer.ring.to * 3 < minimum) continue
            // Falling into you, the leftovers have left their grid buckets.
            if (game.collapsing) { for (item in layer.statics) if (item.active && item.radius >= minimum) visible += item }
            else layer.grid.query(left - margin, top - margin, right + margin, bottom + margin) { item ->
                if (item.stamp != stamp && item.active && (item.radius >= minimum || item.prop.bonus)) { item.stamp = stamp; visible += item }
            }
            for (item in layer.movers) if (item.active && item.radius >= minimum) visible += item
        }
        for (item in game.loose) if (item.active) visible += item
        for (cover in game.covers) if (cover.active) {
            if (cover.x + cover.radius * 1.5 < left || cover.x - cover.radius * 1.5 > right || cover.y + cover.radius * 1.5 < top || cover.y - cover.radius * 1.5 > bottom) continue
            drawItem(c, cover, r)
        }
        if (game.ring.theme.ordinal >= Theme.PLANETS.ordinal) visible.sortByDescending { it.radius } else visible.sortBy { it.y + it.radius * .5 }
        for (item in visible) {
            if (item.x + item.radius * 1.5 < left - margin || item.x - item.radius * 1.5 > right + margin || item.y + item.radius * 1.5 < top - margin || item.y - item.radius * 1.5 > bottom + margin) continue
            drawItem(c, item, r)
        }
    }

    private fun drawItem(c: Canvas, item: GluttonGame.Item, r: Double) {
        val x = sx(item.x); val y = sy(item.y); val rad = (item.radius * zoom).toFloat()
        if (item.prop.bonus) {
            p.color = 0x55FFD46F; c.drawCircle(x, y, rad * 1.3f, p)
            art.item(c, item.prop, x, y + sin(visualTime * 3f + item.id) * rad * .15f, rad, 0f)
            return
        }
        val edible = game.canEat(item)
        // Only the really rewarding bites get a halo; the rest stays clean.
        if (edible && item.radius >= r * GluttonGame.BIG_BITE) {
            p.color = 0x4450BA99; c.drawCircle(x, y, rad * 1.25f, p)
            p.color = 0x997BE4AC.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = rad * .08f
            c.drawCircle(x, y, rad * 1.25f, p); p.style = Paint.Style.FILL
        }
        val alpha = if (item.cooldown > 0f) 150 + (sin(visualTime * 30f) * 60f).toInt() else 255
        val flip = if (item.moves && abs(item.vx) > item.radius * .3) item.vx < 0 else item.flip
        art.item(c, item.prop, x, y, rad, item.angle, alpha, flip)
        if (item.moves && item.orbit == null) {
            if (item.stunned > 0f) for (i in 0..2) {
                val a = visualTime * 3f + i * 2.1f
                art.star(c, x + cos(a) * rad, y - rad + sin(a) * rad * .3f, rad * .25f, GluttonArt.GOLD)
            } else if (item.alert) art.alarm(c, x, y - rad * 1.1f, rad * .8f, !edible)
        }
    }

    private fun drawSuction(c: Canvas) {
        if (!running || game.finished) return
        val strong = game.burst > 0f
        val gx = sx(game.x); val gy = sy(game.y); val px = (game.radius * zoom).toFloat()
        val reach = (game.suctionRadius * zoom).toFloat()
        p.color = GluttonArt.MINT
        p.style = Paint.Style.STROKE; p.strokeWidth = px * if (strong) .08f else .04f
        for (i in 0 until if (strong) 3 else 1) {
            val phase = (visualTime * 1.6f + i / 3f) % 1f
            val rr = px + (reach - px) * (1f - phase)
            p.alpha = ((if (strong) 150 else 65) * sin(phase * PI).toFloat()).toInt().coerceIn(0, 255)
            arc.set(gx - rr, gy - rr, gx + rr, gy + rr)
            c.drawArc(arc, visualTime * 100f + i * 120f, if (strong) 220f else 140f, false, p)
        }
        p.alpha = 255; p.style = Paint.Style.FILL
    }

    /** Map of the ring you are in, drawn once per ring, with you on it. */
    private fun drawMap(c: Canvas) {
        if (!running) return
        val size = 82f * density; val left = 15f * density; val top = height - size - 12f * density
        val ring = game.ring
        if (mapRing != ring.index || map == null) { map = renderMap(ring, size.toInt()); mapRing = ring.index }
        p.color = 0xDBFFF6E8.toInt(); c.drawRoundRect(left, top, left + size, top + size, 13f * density, 13f * density, p)
        val inset = 6f * density; val span = size - inset * 2
        map?.let { c.drawBitmap(it, null, RectF(left + inset, top + inset, left + inset + span, top + inset + span), p) }
        fun mx(x: Double) = (left + inset + (x + ring.half) / (2 * ring.half) * span).toFloat()
        fun my(y: Double) = (top + inset + (y + ring.half) / (2 * ring.half) * span).toFloat()
        // Food your size, so the rich corners show up: that is where to head.
        if (game.elapsed >= nextFoodMap) { foodMap(); nextFoodMap = game.elapsed + .5f }
        p.color = GluttonArt.MINT
        for (i in 0 until foodDots) c.drawCircle(mx(foodSpots[i * 2]), my(foodSpots[i * 2 + 1]), 1.1f * density, p)
        game.layers[ring.index]?.let { layer ->
            p.color = GluttonArt.GOLD
            for (item in layer.statics) if (item.active && item.prop.bonus) c.drawCircle(mx(item.x), my(item.y), 1.8f * density, p)
        }
        p.color = GluttonArt.INK
        c.drawCircle(mx(game.x), my(game.y), 3.3f * density, p)
        p.color = Color.WHITE
        c.drawCircle(mx(game.x), my(game.y), 1.4f * density, p)
    }

    /** Up to [MAP_DOTS] things of the ring you are in that would be a worthwhile bite now. */
    private fun foodMap() {
        foodDots = 0
        val layer = game.layers[game.ring.index] ?: return
        val r = game.radius
        val list = layer.statics
        // Cleaning up the universe: every crumb left shows, however small.
        val step = if (game.finale) 1 else max(1, list.size / (MAP_DOTS * 4))
        var i = (game.elapsed * 7).toInt() % step
        while (i < list.size && foodDots < MAP_DOTS) {
            val item = list[i]
            if (item.active && !item.prop.bonus && (game.finale || game.canEat(item) && item.radius >= r * .25)) {
                foodSpots[foodDots * 2] = item.x; foodSpots[foodDots * 2 + 1] = item.y; foodDots++
            }
            i += step
        }
    }

    private fun renderMap(ring: Ring, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size.coerceAtLeast(8), size.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = size / (2 * ring.half)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun box(g: Ground, x0: Double, y0: Double, x1: Double, y1: Double, round: Boolean) {
            paint.color = averages[g.ordinal].takeIf { it != 0 } ?: FALLBACK[g.ordinal]
            val r = RectF(((x0 + ring.half) * scale).toFloat(), ((y0 + ring.half) * scale).toFloat(), ((x1 + ring.half) * scale).toFloat(), ((y1 + ring.half) * scale).toFloat())
            if (round) canvas.drawOval(r, paint) else canvas.drawRect(r, paint)
        }
        box(ring.ground, -ring.half, -ring.half, ring.half, ring.half, ring.round)
        for (patch in ring.patches) box(patch.ground, patch.x0, patch.y0, patch.x1, patch.y1, patch.round)
        paint.style = Paint.Style.STROKE
        for (annulus in ring.annuli) {
            paint.color = averages[annulus.ground.ordinal].takeIf { it != 0 } ?: FALLBACK[annulus.ground.ordinal]
            paint.strokeWidth = max(1f, (annulus.width * scale).toFloat())
            canvas.drawCircle(size / 2f, size / 2f, (annulus.radius * scale).toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
        if (ring.index > 0) {
            paint.color = 0x66000000; val h = ring.inner
            val hole = RectF(((-h + ring.half) * scale).toFloat(), ((-h + ring.half) * scale).toFloat(), ((h + ring.half) * scale).toFloat(), ((h + ring.half) * scale).toFloat())
            if (game.world.rings[ring.index - 1].round) canvas.drawOval(hole, paint) else canvas.drawRect(hole, paint)
        }
        paint.color = WALL
        for (wall in ring.walls) {
            if (wall.horizontal) canvas.drawRect(((wall.from + ring.half) * scale).toFloat(), ((wall.line + ring.half) * scale).toFloat() - .6f, ((wall.until + ring.half) * scale).toFloat(), ((wall.line + ring.half) * scale).toFloat() + .6f, paint)
            else canvas.drawRect(((wall.line + ring.half) * scale).toFloat() - .6f, ((wall.from + ring.half) * scale).toFloat(), ((wall.line + ring.half) * scale).toFloat() + .6f, ((wall.until + ring.half) * scale).toFloat(), paint)
        }
        return bitmap
    }

    private fun drawDirectionHint(c: Canvas) {
        val food = nearest ?: return
        val speed = hypot(game.vx, game.vy)
        if (!food.active || game.finished || speed > game.moveSpeed * .4) return
        val dx = food.x - game.x; val dy = food.y - game.y
        val distance = hypot(dx, dy)
        if (distance < game.radius * 6) return
        val reach = game.radius * 1.9 + sin(visualTime * 5f) * game.radius * .15
        val px = (game.radius * zoom).toFloat()
        c.save(); c.translate(sx(game.x + dx / distance * reach), sy(game.y + dy / distance * reach))
        c.rotate(atan2(dy, dx).toFloat() * 180f / PI.toFloat()); c.scale(px / 16f, px / 16f)
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
                    hasSteered = true; game.steer((nx / maxDrag).toDouble(), (ny / maxDrag).toDouble())
                } else game.steer(0.0, 0.0)
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) cancelInput()
            MotionEvent.ACTION_UP -> { cancelInput(); performClick() }
            MotionEvent.ACTION_CANCEL -> cancelInput()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); zoom = 0.0; cameraX = game.x; cameraY = game.y; cancelInput(); mapRing = -1
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); audio = GluttonAudio(context)
        atlas.prepare(game.ring.theme)
        if (running) postOnAnimation(frame)
    }
    override fun onDetachedFromWindow() {
        setRunning(false); audio?.release(); audio = null; atlas.release(); super.onDetachedFromWindow()
    }

    companion object {
        /** The screen's short side shows this many gobbler radii: you take a thirteenth of it. */
        const val VIEW_SPAN = 26.0
        const val MAP_DOTS = 160
        /** Ground textures fade into their average colour between these tile sizes, in pixels. */
        const val FADE_FROM = 12.0
        const val FADE_TO = 40.0
        /** Big Bang timeline, in seconds after the last bite: implosion, then the blast and the new sky. */
        const val BANG_AT = .75f
        const val FIREBALL = 1.1f
        const val BANG_END = 4.2f
        const val STARS = 120
        const val DAWN = 0xFFFFFDF7.toInt()
        const val FLASH = 0xFFFFF6DC.toInt()
        const val SINGULARITY = 0xFF1B1530.toInt()
        const val NEW_SKY = 0xFF0D1030.toInt()
        const val CORAL_HOT = 0xFFFF7A5C.toInt()
        private val SPARKS = intArrayOf(Color.WHITE, GluttonArt.GOLD, GluttonArt.GOLD, GluttonArt.CORAL, GluttonArt.MINT, 0xFFB07CE8.toInt(), 0xFF8FC2FF.toInt())
        const val LOT = 0xFFB9A98A.toInt()
        const val WALL = 0xFF8C7F99.toInt()
        /** Flat colours while a texture loads, and for the map. */
        val FALLBACK = intArrayOf(0xFFD9A86C.toInt(), 0xFFE7C2A6.toInt(), 0xFF8FA9C9.toInt(), 0xFFD7ECEC.toInt(), 0xFF8CC063.toInt(),
            0xFF5E5F63.toInt(), 0xFFC4C5C2.toInt(), 0xFFD8BE95.toInt(), 0xFFE0B65C.toInt(), 0xFF9CC46E.toInt(), 0xFF4F8A4C.toInt(),
            0xFF6FB8E0.toInt(), 0xFFB4B6B8.toInt(), 0xFFE9C985.toInt(), 0xFF2E5C9A.toInt(), 0xFF1D2650.toInt())
        /** Space shifts from navy (planets) to violet (the galaxy) to the deep black of the cosmic web. */
        private val TINTS: Array<ColorFilter?> = arrayOf(null, null, null, null, null, null, null,
            LightingColorFilter(0xFFE6DCFF.toInt(), 0x00080010), LightingColorFilter(0xFFF0C8F0.toInt(), 0x00140620),
            LightingColorFilter(0xFF9C9CB4.toInt(), 0x00000004))
    }
}
