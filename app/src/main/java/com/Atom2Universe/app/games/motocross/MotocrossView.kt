package com.Atom2Universe.app.games.motocross

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import kotlin.math.*
import kotlin.random.Random

/** Simulation et rendu sur le thread UI : entrées, pause et reprises sont atomiques. */
class MotocrossView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), Choreographer.FrameCallback {

    data class Stats(
        val seed: Int, val distance: Int, val length: Int, val speed: Int,
        val seconds: Int, val faults: Int, val checkpoint: Int, val checkpointCount: Int
    )
    var onStats: ((Stats) -> Unit)? = null
    var onReward: ((Int) -> Unit)? = null
    var onControlsCleared: (() -> Unit)? = null
    private val prefs = context.getSharedPreferences("motocross_save", Context.MODE_PRIVATE)
    private var track = MotocrossTrack(prefs.getInt("trial_seed", Random.nextInt(100000, 1000000)))
    private val bike = MotocrossBike()
    private var checkpoint = prefs.getInt("trial_checkpoint", 0).coerceIn(0, track.checkpoints.lastIndex)
    private var elapsed = prefs.getFloat("trial_time", 0f)
    private var faults = prefs.getInt("trial_faults", 0)
    private var frontier = prefs.getFloat("trial_frontier", 0f)
    private var paid = prefs.getInt("trial_paid", 0)
    private var finished = prefs.getBoolean("trial_finished", false)
    private var best = prefs.getInt("trial_best", 0)
    private var running = false
    private var lastFrame = 0L
    private var accumulator = 0f
    private var hudClock = 0f
    private var checkpointNotice = 0f
    private var crashAge = 0f
    private var camX = 0f
    private var camY = 0f
    private var zoom = 1f
    private var throttle = false
    private var brake = false
    private var leanBack = false
    private var leanForward = false
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val path = Path()
    private var sky: Shader? = null
    private val dp = resources.displayMetrics.density

    init {
        isFocusable = true
        isClickable = true
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        contentDescription = context.getString(R.string.motocross_surface_description)
        bike.reset(if (finished) track.finishX else track.checkpoints[checkpoint], track)
        snapCamera()
        save()
    }

    /** 0/1 : équilibre arrière/avant, 2/3 : frein/gaz. */
    fun setControl(control: Int, held: Boolean) {
        if (held && (bike.crashed || finished)) return
        when (control) {
            0 -> leanBack = held
            1 -> leanForward = held
            2 -> brake = held
            3 -> throttle = held
        }
    }

    fun clearControls() {
        throttle = false; brake = false; leanBack = false; leanForward = false
        onControlsCleared?.invoke()
    }

    fun resume() {
        if (running) return
        running = true; lastFrame = 0L; accumulator = 0f
        Choreographer.getInstance().postFrameCallback(this)
        reportStats()
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        clearControls()
        save()
    }

    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }

    /** Le bouton de reprise conserve le parcours et son point de passage. */
    fun resetGame() {
        if (finished) {
            checkpoint = 0; elapsed = 0f; faults = 0; finished = false
        } else if (!bike.crashed) faults++
        bike.reset(track.checkpoints[checkpoint], track)
        clearControls(); crashAge = 0f; accumulator = 0f; checkpointNotice = 0f
        snapCamera(); save(); reportStats(); invalidate()
    }

    fun restartTrack() {
        checkpoint = 0; elapsed = 0f; faults = 0; finished = false
        bike.reset(track.checkpoints[0], track)
        clearControls(); accumulator = 0f; crashAge = 0f; checkpointNotice = 0f
        // frontier/paid restent conservés : recommencer ne duplique pas les gains.
        snapCamera(); save(); reportStats(); invalidate()
    }

    fun newTrack() {
        var seed = Random.nextInt(100000, 1000000)
        if (seed == track.seed) seed = if (seed == 999999) 100000 else seed + 1
        track = MotocrossTrack(seed)
        checkpoint = 0; elapsed = 0f; faults = 0; frontier = 0f; paid = 0
        finished = false; checkpointNotice = 0f; crashAge = 0f
        clearControls(); accumulator = 0f
        bike.reset(track.checkpoints[0], track)
        snapCamera(); save(); reportStats(); invalidate()
    }

    private fun snapCamera() {
        camX = bike.x + 5f; camY = bike.y + 1.8f; zoom = 1f
        ViewCompat.setStateDescription(this, if (finished) context.getString(R.string.motocross_finished) else null)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrame == 0L) 0f else ((frameTimeNanos - lastFrame) / 1e9f).coerceIn(0f, .066f)
        lastFrame = frameTimeNanos
        accumulator = min(accumulator + dt, .066f)
        checkpointNotice = max(0f, checkpointNotice - dt)
        if (bike.crashed) crashAge += dt
        while (accumulator >= STEP) {
            if (!finished && !bike.crashed) {
                val lean = (if (leanForward) 1f else 0f) - (if (leanBack) 1f else 0f)
                if (throttle || brake || lean != 0f || elapsed > 0f) elapsed += STEP
                bike.step(STEP, throttle, brake, lean, track)
                if (bike.crashed) {
                    faults++; crashAge = 0f; clearControls(); save()
                    ViewCompat.setStateDescription(this, context.getString(R.string.motocross_crashed))
                } else updateProgress()
            }
            accumulator -= STEP
        }
        val smooth = 1f - exp(-4.5f * dt)
        val lookAhead = (5f + max(0f, bike.vx) * .12f).coerceAtMost(8.5f)
        val framingLoop = bike.activeLoop ?: track.loops.firstOrNull {
            abs(bike.x - it.x) < it.radius + 10f
        }
        val targetX = framingLoop?.x ?: (bike.x + lookAhead)
        camX += (targetX - camX) * smooth
        val aheadGround = track.heightBelow(bike.x + 12f, bike.y + 3f)
        val targetY = framingLoop?.y
            ?: max(bike.y + 1.8f, aheadGround + 1.8f).coerceAtMost(bike.y + 4.5f)
        camY += (targetY - camY) * (1f - exp(-3f * dt))
        val altitude = (bike.y - track.heightBelow(bike.x, bike.y) - 1f).coerceAtLeast(0f)
        var targetZoom = (1f - (hypot(bike.vx, bike.vy) / 28f).coerceIn(0f, 1f) * .18f - altitude * .02f)
            .coerceIn(.60f, 1f)
        if (framingLoop != null) {
            targetZoom = min(1f, 16f / (framingLoop.radius * 2f + 6f))
        }
        zoom += (targetZoom - zoom) * smooth
        hudClock += dt
        if (hudClock >= .1f) { hudClock = 0f; reportStats() }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun updateProgress() {
        // On valide un checkpoint sur ses roues, jamais en survolant le drapeau.
        val next = checkpoint + 1
        if (next < track.checkpoints.size && bike.x >= track.checkpoints[next] &&
            (bike.rear.grounded || bike.front.grounded) && abs(bike.angle) < .65f) {
            checkpoint = next; checkpointNotice = 1.5f; save()
        }
        val distance = (bike.x - track.checkpoints[0]).coerceIn(0f, courseLength())
        if (distance > frontier) frontier = distance
        best = max(best, frontier.toInt())
        val earned = NeutrinoRewards.perDistance(frontier)
        if (earned > paid) {
            val difference = earned - paid
            paid = earned
            save() // La même portion ne rapporte pas à nouveau après une reprise.
            onReward?.invoke(difference)
        }
        if (bike.x >= track.finishX && (bike.rear.grounded || bike.front.grounded)) {
            finished = true; clearControls(); save(); reportStats()
            ViewCompat.setStateDescription(this, context.getString(R.string.motocross_finished))
        }
    }

    private fun courseLength() = track.finishX - track.checkpoints[0]

    private fun reportStats() {
        onStats?.invoke(Stats(track.seed,
            (bike.x - track.checkpoints[0]).coerceIn(0f, courseLength()).toInt(),
            courseLength().toInt(), (hypot(bike.vx, bike.vy) * 3.6f).toInt(), elapsed.toInt(), faults,
            checkpoint, track.checkpoints.lastIndex))
    }

    private fun save() {
        prefs.edit().putInt("trial_seed", track.seed).putInt("trial_checkpoint", checkpoint)
            .putFloat("trial_time", elapsed).putInt("trial_faults", faults)
            .putFloat("trial_frontier", frontier).putInt("trial_paid", paid)
            .putBoolean("trial_finished", finished).putInt("trial_best", best).apply()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (finished) newTrack() else if (bike.crashed && crashAge > .2f) resetGame()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sky = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(24, 43, 65), Color.rgb(85, 126, 145), Color.rgb(229, 192, 143)),
            floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSky(canvas)
        val scale = min(width / 32f, height / 16f).coerceAtLeast(1f) * zoom
        canvas.save()
        canvas.translate(width * .5f, height * .53f)
        canvas.scale(scale, -scale)
        canvas.translate(-camX, -camY)
        drawTerrain(canvas, scale)
        drawStructures(canvas, scale)
        drawBike(canvas)
        canvas.restore()
        drawHud(canvas)
    }

    private fun fill(color: Int) { ink.style = Paint.Style.FILL; ink.color = color; ink.shader = null }
    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, thickness: Float) {
        ink.style = Paint.Style.STROKE; ink.color = color; ink.strokeWidth = thickness; ink.shader = null
        canvas.drawLine(x1, y1, x2, y2, ink)
    }
    private fun circle(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill(color); canvas.drawCircle(x, y, radius, ink)
    }

    private fun drawSky(canvas: Canvas) {
        fill(Color.rgb(34, 59, 78)); ink.shader = sky
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ink); ink.shader = null
        circle(canvas, width * .78f, height * .23f, min(width, height) * .09f, Color.argb(30, 255, 226, 174))
        circle(canvas, width * .78f, height * .23f, min(width, height) * .06f, Color.rgb(249, 218, 165))
        // Reliefs continus avec parallaxe ; aucun bitmap ni chargement d'assets.
        for (layer in 0..2) {
            val stride = 100f * dp
            val offset = camX * (3f + layer * 3f) * dp
            val start = floor(offset / stride).toInt() - 1
            path.reset(); path.moveTo(-stride, height.toFloat())
            for (i in start..start + ceil(width / stride).toInt() + 3) {
                val screenX = i * stride - offset
                val wave = sin(i * 1.71f + layer * 2f) * .5f + .5f
                val screenY = height * (.44f + layer * .11f) - wave * height * .17f
                path.lineTo(screenX, screenY)
            }
            path.lineTo(width + stride, height.toFloat()); path.close()
            fill(intArrayOf(Color.rgb(82, 113, 128), Color.rgb(58, 90, 106), Color.rgb(36, 65, 81))[layer])
            canvas.drawPath(path, ink)
        }
    }

    private fun drawTerrain(canvas: Canvas, scale: Float) {
        val left = camX - width / (2f * scale) - 2f
        val right = camX + width / (2f * scale) + 2f
        val bottom = camY - height / scale - 5f
        val first = track.segmentAt(left)
        val last = min(track.points.lastIndex, track.segmentAt(right) + 2)

        // Pins et panneaux en arrière-plan : purement décoratifs, hors de la piste.
        for (i in floor(left / 6f).toInt()..ceil(right / 6f).toInt()) {
            val x = i * 6f + 1f
            val ground = track.height(x)
            val h = 1.4f + abs(sin(i * 4.1f))
            line(canvas, x, ground, x, ground + h, Color.rgb(48, 66, 61), .10f)
            fill(Color.rgb(41, 80, 78))
            path.reset(); path.moveTo(x - .65f, ground + .4f)
            path.lineTo(x, ground + h + .4f); path.lineTo(x + .65f, ground + .4f); path.close()
            canvas.drawPath(path, ink)
        }

        path.reset(); path.moveTo(track.points[first].x, bottom)
        for (i in first..last) path.lineTo(track.points[i].x, track.points[i].y)
        path.lineTo(track.points[last].x, bottom); path.close()
        fill(Color.rgb(60, 48, 42)); canvas.drawPath(path, ink)
        canvas.save(); canvas.clipPath(path)
        // Strates et gravier déterministes : la même piste garde le même aspect.
        for (depth in 1..4) {
            path.reset()
            for (i in first..last) {
                val p = track.points[i]
                val y = p.y - depth * .62f + sin(p.x * .7f + depth) * .09f
                if (i == first) path.moveTo(p.x, y) else path.lineTo(p.x, y)
            }
            ink.style = Paint.Style.STROKE; ink.color = Color.rgb(83, 62, 48); ink.strokeWidth = .09f
            canvas.drawPath(path, ink)
        }
        for (i in floor(left * 2).toInt()..ceil(right * 2).toInt()) {
            val x = i * .5f
            val y = track.height(x) - .35f - abs(sin(i * 19.7f)) * 2.2f
            circle(canvas, x, y, .025f + abs(sin(i * 3.7f)) * .035f, Color.rgb(112, 82, 57))
        }
        canvas.restore()
        path.reset()
        for (i in first..last) {
            val p = track.points[i]
            if (i == first) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        ink.style = Paint.Style.STROKE; ink.color = Color.rgb(155, 113, 66); ink.strokeWidth = .18f
        canvas.drawPath(path, ink)
        ink.color = Color.rgb(234, 193, 122); ink.strokeWidth = .045f
        canvas.drawPath(path, ink)

        // Habillage posé exactement sur le profil physique : bois pour les tables,
        // dalles pour les marches. Aucun obstacle invisible ou décor trompeur.
        for (section in track.sections) {
            if (section.end < left || section.start > right) continue
            if (section.kind != MotocrossTrack.Kind.TABLE && section.kind != MotocrossTrack.Kind.STEPS) continue
            var x = max(section.start, floor(left * 2f) / 2f)
            while (x < min(section.end, right)) {
                val y = track.height(x)
                if (y > .4f) {
                    val slope = atan2(track.height(x + .2f) - track.height(x - .2f), .4f)
                    canvas.save(); canvas.translate(x, y); canvas.rotate(Math.toDegrees(slope.toDouble()).toFloat())
                    val wood = section.kind == MotocrossTrack.Kind.TABLE
                    fill(if (wood) Color.rgb(177, 126, 72) else Color.rgb(131, 143, 142))
                    canvas.drawRoundRect(-.23f, -.20f, .23f, .015f, .025f, .025f, ink)
                    line(canvas, -.21f, .015f, .21f, .015f, if (wood) GOLD else STEEL, .035f)
                    if (wood) circle(canvas, 0f, -.08f, .025f, DARK)
                    canvas.restore()
                }
                x += .5f
            }
        }

        for ((index, x) in track.checkpoints.withIndex()) {
            if (x < left || x > right) continue
            val active = index <= checkpoint
            val color = if (active) MINT else Color.rgb(195, 204, 208)
            line(canvas, x, .06f, x, 1.55f, color, .055f)
            fill(color)
            path.reset(); path.moveTo(x, 1.55f); path.lineTo(x + .65f, 1.37f)
            path.lineTo(x, 1.13f); path.close(); canvas.drawPath(path, ink)
            worldText(canvas, context.getString(R.string.motocross_checkpoint_number, index), x, 1.8f, .25f, color)
        }
        if (track.finishX in left..right) {
            val x = track.finishX
            line(canvas, x, 0f, x, 2.7f, WHITE, .08f)
            for (row in 0..2) for (col in 0..4) {
                fill(if ((row + col) % 2 == 0) WHITE else DARK)
                canvas.drawRect(x + col * .2f, 2.1f + row * .2f, x + (col + 1) * .2f, 2.3f + row * .2f, ink)
            }
            worldText(canvas, context.getString(R.string.motocross_finish_flag), x, 3f, .3f, WHITE)
        }
        // Repères d'obstacles : nombre de chevrons = difficulté, avant la montée.
        track.sections.filter { it.start in left..right && it.kind != MotocrossTrack.Kind.REST }.forEach { section ->
            val x = section.start + 2f
            line(canvas, x, 0f, x, .65f, Color.rgb(134, 112, 79), .06f)
            fill(DARK); canvas.drawRoundRect(x - .34f, .55f, x + .34f, .93f, .05f, .05f, ink)
            for (j in 0..section.difficulty) {
                val px = x - section.difficulty * .13f + j * .26f
                line(canvas, px - .06f, .65f, px + .04f, .74f, GOLD, .035f)
                line(canvas, px + .04f, .74f, px - .06f, .83f, GOLD, .035f)
            }
        }
    }

    private fun drawStructures(canvas: Canvas, scale: Float) {
        val left = camX - width / (2f * scale) - 3f
        val right = camX + width / (2f * scale) + 3f
        for (road in track.roads) {
            if (road.maxX < left || road.minX > right) continue
            if (road.loopId < 0) {
                // Charpente en arrière-plan ; seules les bandes de roulement
                // constituent une surface solide dans le plan de la moto.
                for (i in 0 until road.points.lastIndex step 50) {
                    val p = road.points[i]
                    if (p.x !in left..right || p.y - track.height(p.x) < 2f) continue
                    val q = road.points[min(i + 50, road.points.lastIndex)]
                    val floor = track.height(p.x)
                    val tint = Color.argb(140, 88, 118, 128)
                    line(canvas, p.x, floor, p.x, p.y - .18f, tint, .14f)
                    line(canvas, p.x, p.y - 1.3f, q.x, q.y - .18f, tint, .09f)
                    line(canvas, p.x, p.y - .18f, q.x, q.y - 1.3f, tint, .09f)
                }
            }
            path.reset()
            for ((i, p) in road.points.withIndex()) {
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            ink.style = Paint.Style.STROKE; ink.strokeWidth = .16f
            ink.color = if (road.loopId < 0) STEEL else GOLD
            canvas.drawPath(path, ink)
            ink.strokeWidth = .045f; ink.color = WHITE
            canvas.drawPath(path, ink)
            if (road.loopId < 0) {
                for (i in road.points.indices step 7) {
                    val p = road.points[i]
                    if (p.x in left..right) circle(canvas, p.x, p.y, .04f, DARK)
                }
            }
        }
        for (loop in track.loops) {
            if (loop.x + loop.radius < left || loop.x - loop.radius > right) continue
            val r = loop.radius
            // La branche descendante passe derrière la voie d'entrée/sortie.
            // Les flèches rendent le sens de circulation lisible même au plafond.
            for (i in 1..7) {
                val angle = i * PI.toFloat() / 4f
                val nx = -sin(angle); val ny = cos(angle)
                val tx = cos(angle); val ty = sin(angle)
                val x = loop.x + sin(angle) * (r - .55f)
                val y = loop.y - cos(angle) * (r - .55f)
                val color = if (i >= 5) STEEL else MINT
                line(canvas, x - tx * .3f + nx * .2f, y - ty * .3f + ny * .2f,
                    x + tx * .25f, y + ty * .25f, color, .07f)
                line(canvas, x - tx * .3f - nx * .2f, y - ty * .3f - ny * .2f,
                    x + tx * .25f, y + ty * .25f, color, .07f)
            }
            line(canvas, loop.x - 3f, .015f, loop.x + 3f, .015f, GOLD, .10f)
            worldText(canvas, context.getString(R.string.motocross_loop_sign),
                loop.x, loop.y * 2f + 1.2f, .65f, GOLD)
        }
        for (section in track.sections) {
            if (section.kind != MotocrossTrack.Kind.BRIDGE || section.start !in left..right) continue
            val x = section.start + 8f
            line(canvas, x, 0f, x, 2.3f, STEEL, .07f)
            worldText(canvas, context.getString(R.string.motocross_bridge_sign), x, 2.6f, .55f, MINT)
        }
    }

    private fun drawBike(canvas: Canvas) {
        val ground = track.heightBelow(bike.x, bike.y)
        val clearance = (bike.y - ground).coerceIn(0f, 5f)
        fill(Color.argb((65f / (1f + clearance)).toInt(), 0, 0, 0))
        canvas.drawOval(bike.x - 1f, ground + .035f, bike.x + 1f, ground + .12f, ink)
        if (throttle && bike.rear.grounded && bike.vx > 1f && !bike.crashed) {
            for (i in 1..7) {
                val life = ((elapsed * 2f + i * .13f) % 1f)
                val x = bike.rear.surfaceX - bike.rear.normalY * life * 1.7f + bike.rear.normalX * (.12f + life * .3f)
                val y = bike.rear.surfaceY + bike.rear.normalX * life * 1.7f + bike.rear.normalY * (.12f + life * .3f)
                circle(canvas, x, y,
                    .03f + life * .10f, Color.argb(((1f - life) * 100).toInt(), 219, 179, 116))
            }
        }
        drawWheel(canvas, bike.rear)
        drawWheel(canvas, bike.front)
        canvas.save(); canvas.translate(bike.x, bike.y)
        canvas.rotate(Math.toDegrees(bike.angle.toDouble()).toFloat())
        val rearY = -MotocrossBike.REST + bike.rear.compression
        val frontY = -MotocrossBike.REST + bike.front.compression
        // Bras oscillant, amortisseur arrière et fourche télescopique.
        line(canvas, -.76f, rearY, -.10f, -.08f, STEEL, .10f)
        line(canvas, -.62f, rearY + .03f, -.22f, .23f, DARK, .13f)
        line(canvas, -.62f, rearY + .03f, -.22f, .23f, GOLD, .055f)
        for (i in 1..5) {
            val t = i / 7f
            val x = -.62f + .4f * t
            val y = rearY + .03f + (.20f - rearY) * t
            line(canvas, x - .055f, y + .035f, x + .055f, y - .035f, WHITE, .025f)
        }
        line(canvas, .76f, frontY, .50f, .28f, STEEL, .105f)
        line(canvas, .62f, frontY + (.28f - frontY) * .53f, .50f, .28f, GOLD, .13f)
        line(canvas, -.45f, .18f, -.08f, -.17f, MINT, .085f)
        line(canvas, -.08f, -.17f, .50f, .23f, MINT, .085f)
        line(canvas, .50f, .23f, -.45f, .18f, MINT, .09f)
        circle(canvas, -.03f, -.04f, .18f, DARK)
        circle(canvas, -.03f, -.04f, .11f, STEEL)
        line(canvas, -.51f, .28f, -.12f, .28f, DARK, .13f)
        line(canvas, -.78f, .20f, -.44f, .24f, MINT, .095f)
        line(canvas, .50f, .24f, .88f, .15f, MINT, .09f)
        line(canvas, .50f, .25f, .44f, .43f, STEEL, .055f)
        line(canvas, .44f, .43f, .62f, .43f, DARK, .07f)
        line(canvas, -.18f, -.15f, .05f, -.15f, STEEL, .055f)
        drawRider(canvas)
        canvas.restore()
    }

    private fun drawWheel(canvas: Canvas, wheel: MotocrossBike.Wheel) {
        circle(canvas, wheel.x, wheel.y, MotocrossBike.RADIUS, DARK)
        ink.style = Paint.Style.STROKE; ink.color = STEEL; ink.strokeWidth = .035f
        canvas.drawCircle(wheel.x, wheel.y, .235f, ink)
        for (i in 0 until 10) {
            val a = wheel.spin + i * PI.toFloat() / 5f
            val ca = cos(a); val sa = sin(a)
            line(canvas, wheel.x + ca * .06f, wheel.y + sa * .06f,
                wheel.x + ca * .22f, wheel.y + sa * .22f, STEEL, .016f)
            line(canvas, wheel.x + ca * .287f, wheel.y + sa * .287f,
                wheel.x + ca * .319f, wheel.y + sa * .319f, Color.rgb(65, 72, 78), .04f)
        }
        circle(canvas, wheel.x, wheel.y, .065f, GOLD)
    }

    private fun drawRider(canvas: Canvas) {
        val lean = bike.lean
        val crouch = bike.impact * .08f
        val hipX = -.25f + lean * .18f
        val hipY = .40f - crouch
        val shoulderX = .02f + lean * .29f
        val shoulderY = .83f - crouch
        // Les genoux et coudes se plient par cinématique inverse ; mains et pieds
        // restent sur leurs appuis pendant le déplacement du bassin.
        limb(canvas, hipX, hipY, -.02f, -.11f, .40f, .40f, 1f, Color.rgb(30, 46, 66), .13f)
        limb(canvas, shoulderX, shoulderY, .54f, .43f, .34f, .35f, -1f, Color.rgb(200, 115, 63), .10f)
        line(canvas, hipX, hipY, shoulderX, shoulderY, ORANGE, .25f)
        line(canvas, hipX - .05f, hipY + .05f, shoulderX - .06f, shoulderY - .03f, DARK, .075f)
        limb(canvas, hipX + .06f, hipY, .02f, -.13f, .40f, .40f, 1f, Color.rgb(57, 78, 99), .13f)
        limb(canvas, shoulderX + .04f, shoulderY - .02f, .57f, .43f, .34f, .35f, -1f, ORANGE, .095f)
        line(canvas, -.03f, -.12f, .17f, -.12f, DARK, .10f)
        circle(canvas, .57f, .43f, .06f, DARK)
        val headX = bike.headLocalX
        val headY = bike.headLocalY
        line(canvas, shoulderX, shoulderY, headX, headY - .08f, DARK, .11f)
        circle(canvas, headX, headY, .205f, WHITE)
        line(canvas, headX + .01f, headY + .06f, headX + .21f, headY + .04f, DARK, .11f)
        line(canvas, headX + .06f, headY + .16f, headX + .27f, headY + .10f, ORANGE, .055f)
        line(canvas, headX + .05f, headY - .12f, headX + .20f, headY - .06f, WHITE, .07f)
    }

    private fun limb(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float,
                     upper: Float, lower: Float, bend: Float, color: Int, thickness: Float) {
        val dx = bx - ax; val dy = by - ay
        val distance = hypot(dx, dy).coerceAtLeast(.001f)
        val d = distance.coerceIn(abs(upper - lower) + .001f, upper + lower - .001f)
        val along = (upper * upper - lower * lower + d * d) / (2f * d)
        val side = sqrt(max(0f, upper * upper - along * along)) * bend
        val jointX = ax + dx / distance * along - dy / distance * side
        val jointY = ay + dy / distance * along + dx / distance * side
        line(canvas, ax, ay, jointX, jointY, color, thickness)
        line(canvas, jointX, jointY, bx, by, color, thickness * .84f)
        circle(canvas, jointX, jointY, thickness * .55f, color)
    }

    private fun worldText(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        canvas.save(); canvas.translate(x, y); canvas.scale(1f, -1f)
        textPaint.textSize = size; textPaint.textAlign = Paint.Align.CENTER; textPaint.color = color
        canvas.drawText(text, 0f, 0f, textPaint); canvas.restore()
    }

    private fun drawHud(canvas: Canvas) {
        val margin = 16f * dp
        val barWidth = width - margin * 2f
        fill(Color.argb(120, 13, 24, 36))
        canvas.drawRoundRect(margin, 10f * dp, width - margin, 15f * dp, 3f * dp, 3f * dp, ink)
        fill(MINT)
        val progress = ((bike.x - 3f) / courseLength()).coerceIn(0f, 1f)
        canvas.drawRoundRect(margin, 10f * dp, margin + barWidth * progress, 15f * dp, 3f * dp, 3f * dp, ink)
        if (checkpointNotice > 0f && !bike.crashed && !finished) {
            screenText(canvas, context.getString(R.string.motocross_checkpoint_saved, checkpoint),
                width / 2f, 43f * dp, 14f * dp, MINT)
        } else if (bike.x < 11f && !bike.crashed && !finished) {
            screenText(canvas, context.getString(R.string.motocross_controls_hint),
                width / 2f, 43f * dp, 12f * dp, WHITE)
        } else if (!bike.crashed && !finished) {
            val feature = track.sections.firstOrNull {
                bike.x >= it.start + 5f && bike.x <= it.start + 36f &&
                    (it.kind == MotocrossTrack.Kind.LOOP || it.kind == MotocrossTrack.Kind.BRIDGE)
            }
            if (feature != null) {
                val hint = if (feature.kind == MotocrossTrack.Kind.LOOP) R.string.motocross_loop_hint
                    else R.string.motocross_bridge_hint
                screenText(canvas, context.getString(hint), width / 2f, 43f * dp, 12f * dp, WHITE)
            }
        }
        if (bike.crashed || finished) {
            fill(Color.argb(180, 10, 20, 32))
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ink)
            val title = context.getString(if (finished) R.string.motocross_finished else R.string.motocross_crashed)
            val titleSize = min(27f * dp, width / 14f)
            screenText(canvas, title, width / 2f, height * .36f, titleSize, if (finished) MINT else ORANGE)
            screenText(canvas, context.getString(R.string.motocross_run_summary, elapsed.toInt() / 60,
                elapsed.toInt() % 60, faults), width / 2f, height * .50f, 16f * dp, WHITE)
            val hint = context.getString(if (finished) R.string.motocross_next_hint else R.string.motocross_retry_hint)
            screenText(canvas, hint, width / 2f, height * .65f, 13f * dp, WHITE)
        }
    }

    private fun screenText(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        textPaint.textSize = size; textPaint.textAlign = Paint.Align.CENTER; textPaint.color = color
        val available = width - 24f * dp
        if (textPaint.measureText(text) > available) textPaint.textSize *= available / textPaint.measureText(text)
        canvas.drawText(text, x, y, textPaint)
    }

    private companion object {
        const val STEP = 1f / 240f
        val DARK = Color.rgb(17, 27, 39)
        val WHITE = Color.rgb(235, 242, 242)
        val STEEL = Color.rgb(151, 174, 187)
        val MINT = Color.rgb(101, 225, 194)
        val GOLD = Color.rgb(242, 195, 108)
        val ORANGE = Color.rgb(246, 147, 88)
    }
}
