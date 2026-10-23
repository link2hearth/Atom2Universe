package com.Atom2Universe.app.games.enamel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.bigger.BiggerSoundEngine
import kotlin.math.hypot
import kotlin.math.min

class EnamelView(context: Context) : View(context) {
    val game = EnamelGame()
    var bestScore = 0
    var discoveredTier = -1
    var sound: BiggerSoundEngine? = null
    var onStateChanged: (() -> Unit)? = null
    var onGameOver: (() -> Unit)? = null
    private val dp = resources.displayMetrics.density
    private val colors = SciencePalette(context)
    private val art = EnamelPainter()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .035f
        pathEffect = DashPathEffect(floatArrayOf(.13f, .12f), 0f)
    }
    private var board: Bitmap? = null
    private var scale = 1f
    private var left = 0f
    private var top = 0f
    private var active = false
    private var last = 0L
    private var accumulator = 0f
    private var reported = false
    private var aiming = false
    private var pointer = -1
    private var startY = 0f
    private var startX = 0f
    private var launchX = 5f
    private var launchY = game.launchY
    private var aimX = 0f
    private var aimY = -1f
    private var power = 0f
    private var blocked = false
    private var messageLife = 0f
    private var message = ""
    private val replay = RectF()
    private data class Ring(val event: EnamelEvent, var life: Float = .8f)
    private val rings = ArrayList<Ring>()

    init { isFocusable = true; contentDescription = context.getString(R.string.enamel_controls) }

    fun resume() { active = true; last = 0L; postInvalidateOnAnimation() }
    fun pause() { active = false; cancelAim(); last = 0L }
    fun reset() {
        game.reset(); reported = false; rings.clear(); messageLife = 0f; accumulator = 0f
        cancelAim(); launchX = 5f; launchY = game.launchY; blocked = false; last = 0L
        onStateChanged?.invoke(); invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val availableW = (w - 12 * dp).coerceAtLeast(1f)
        val availableH = (h - 52 * dp).coerceAtLeast(1f)
        val boardHeight = (availableH / (availableW / 10.4f) - .4f)
            .coerceIn(EnamelRules.MIN_HEIGHT, EnamelRules.MAX_HEIGHT)
        game.resize(boardHeight)
        scale = min(availableW / 10.4f, availableH / (game.height + .4f)).coerceAtLeast(.1f)
        left = (w - 10 * scale) / 2
        top = 4 * dp + maxOf(0f, (availableH - (game.height + .4f) * scale) / 2) + .2f * scale
        launchY = game.launchY
        val bw = kotlin.math.ceil(10.4f * scale).toInt().coerceAtLeast(1)
        val bh = kotlin.math.ceil((game.height + .4f) * scale).toInt().coerceAtLeast(1)
        board?.recycle()
        board = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it); c.scale(scale, scale); c.translate(.2f, .2f); art.board(c, game.height)
        }
        cancelAim()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var dt = 0f
        if (active) {
            val now = System.nanoTime()
            dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, .05f)
            last = now
            val wasMoving = game.moving
            accumulator += dt
            while (accumulator >= 1f / 120) { game.step(1f / 120); accumulator -= 1f / 120 }
            for (event in game.events) {
                rings.add(Ring(event))
                sound?.onMerge(event.tier, event.chain)
                if (event.finale) sound?.onAnnihilation()
                if (event.tier > discoveredTier) { discoveredTier = event.tier; sound?.onDiscovery() }
            }
            game.events.clear()
            if (wasMoving && !game.moving) {
                onStateChanged?.invoke()
            }
            if (game.over && !reported) { reported = true; sound?.onGameOver(); onGameOver?.invoke() }
        }
        rings.forEach { it.life -= dt }; rings.removeAll { it.life <= 0f }
        messageLife -= dt
        canvas.drawColor(colors.background)
        board?.let { canvas.drawBitmap(it, left - .2f * scale, top - .2f * scale, null) }
        canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale)
        canvas.save(); canvas.clipRect(0f, 0f, 10f, game.height)
        drawBase(canvas)
        for (puck in game.pucks) art.puck(canvas, puck.tier, puck.x, puck.y, puck.radius, puck.angle)
        for (ring in rings) {
            val t = 1 - ring.life / .8f
            val e = ring.event
            p.style = Paint.Style.STROKE; p.strokeWidth = .045f
            p.color = EnamelPainter.COLORS[e.tier]; p.alpha = (ring.life / .8f * 220).toInt()
            canvas.drawCircle(e.x, e.y, EnamelRules.RADII[e.tier] + t * 1.1f, p)
            p.style = Paint.Style.FILL; p.alpha = 255
            p.color = EnamelPainter.INK; p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            boardText(canvas, context.getString(R.string.enamel_merge_points, e.points, e.chain), e.x, e.y - .6f - t, .36f)
        }
        if (game.canLaunch) {
            val radius = EnamelRules.RADII[game.current]
            launchX = launchX.coerceIn(radius, 10f - radius)
            launchY = launchY.coerceIn(game.baseTop + radius, game.height - radius)
            val clear = game.isLaunchClear(launchX, launchY)
            if (aiming && !blocked) drawAim(canvas)
            art.puck(canvas, game.current, launchX, launchY, radius, alpha = if (clear) 240 else 90)
            p.color = if (clear) EnamelPainter.INK else 0xFFC0503C.toInt()
            p.style = Paint.Style.STROKE; p.strokeWidth = .03f
            canvas.drawCircle(launchX, launchY, radius + .14f, p)
            p.style = Paint.Style.FILL
        }
        canvas.restore()
        canvas.restore()
        if (messageLife > 0f) text(canvas, message, width / 2f, top + (game.baseTop + .5f) * scale, 11f, EnamelPainter.INK)
        drawFooter(canvas)
        if (game.over) drawOver(canvas)
        if (active && (game.moving || rings.isNotEmpty() || messageLife > 0f)) postInvalidateOnAnimation()
        else last = 0L
    }

    private fun drawBase(c: Canvas) {
        if (game.danger > 0f) {
            p.color = 0xFFC0503C.toInt(); p.alpha = (game.danger * 75).toInt()
            c.drawRect(0f, game.baseTop, 10f, game.height, p)
            p.alpha = 255; p.strokeWidth = .065f
            c.drawLine(0f, game.baseTop, 10f * game.danger, game.baseTop, p)
        } else if (aiming) {
            p.color = EnamelPainter.GOLD; p.strokeWidth = .065f
            c.drawLine(0f, game.baseTop, 10f * power, game.baseTop, p)
        }
        // L'aperçu suivant reste dans la base, libérant le bas pour toute la frise.
        if (!aiming || launchX < 7.6f || launchY < game.height - 1.1f) {
            p.color = EnamelPainter.INK; p.alpha = 155
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.textAlign = Paint.Align.RIGHT
            boardText(c, context.getString(R.string.enamel_next), 8.9f, game.height - .39f, .22f)
            art.puck(c, game.next, 9.4f, game.height - .47f, .29f)
        }
        p.alpha = 255
    }

    /** Des tailles de police en pixels évitent que les glyphes de moins d'une unité se superposent. */
    private fun boardText(c: Canvas, label: String, x: Float, y: Float, size: Float) {
        c.save(); c.scale(1f / scale, 1f / scale)
        p.textSize = size * scale
        c.drawText(label, x * scale, y * scale, p)
        c.restore()
    }

    /** Indique la direction et la distance de freinage, avec les rebonds sur les bandes. */
    private fun drawAim(c: Canvas) {
        val length = hypot(aimX, aimY)
        if (length < .001f) return
        var dx = aimX / length; var dy = aimY / length
        val speed = game.shotSpeed(launchY, aimX, aimY, power) ?: return
        var distance = speed * speed / (2 * EnamelRules.DRAG)
        var x = launchX; var y = launchY
        val r = EnamelRules.RADII[game.current]
        line.color = EnamelPainter.INK; line.alpha = 130
        repeat(4) {
            if (distance <= .01f) return@repeat
            val tx = if (dx > .001f) (10 - r - x) / dx else if (dx < -.001f) (r - x) / dx else Float.POSITIVE_INFINITY
            val ty = if (dy > .001f) (game.baseTop - r - y) / dy else if (dy < -.001f) (r - y) / dy else Float.POSITIVE_INFINITY
            val wall = min(tx, ty).coerceAtLeast(0f)
            val travel = min(distance, wall)
            val ex = x + dx * travel; val ey = y + dy * travel
            // Un impact arrête le guide : les mouvements après collision restent à découvrir.
            val pieces = maxOf(1, (travel / .08f).toInt())
            var hit = false
            var stopX = ex; var stopY = ey
            for (i in 1..pieces) {
                val px = x + (ex - x) * i / pieces; val py = y + (ey - y) * i / pieces
                if (game.pucks.any { hypot(it.x - px, it.y - py) <= it.radius + r }) {
                    stopX = px; stopY = py; hit = true; break
                }
            }
            c.drawLine(x, y, stopX, stopY, line)
            if (hit || travel >= distance) {
                p.style = Paint.Style.STROKE; p.strokeWidth = .035f; p.color = EnamelPainter.INK
                c.drawCircle(stopX, stopY, .12f, p); p.style = Paint.Style.FILL
                distance = 0f
            } else {
                x = ex; y = ey
                distance = (distance - travel) * .72f * .72f
                if (tx <= ty) dx = -dx else dy = -dy
            }
        }
    }

    private fun drawFooter(c: Canvas) {
        val unit = (width - 24 * dp) / 12
        val start = 12 * dp + unit / 2
        val y = height - 23 * dp
        p.color = colors.outline; p.strokeWidth = dp
        c.drawLine(start, y, width - start, y, p)
        repeat(12) { tier ->
            val x = start + tier * unit
            val r = min(13 * dp, unit * .35f)
            art.puck(c, tier, x, y, r, alpha = if (tier <= maxOf(discoveredTier, game.current)) 255 else 85)
            if (tier == game.current) {
                p.style = Paint.Style.STROKE; p.color = colors.accent; p.strokeWidth = dp
                c.drawCircle(x, y, r + 3 * dp, p); p.style = Paint.Style.FILL
            }
        }
    }

    private fun drawOver(c: Canvas) {
        p.color = colors.background; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        val cy = height * .42f
        text(c, context.getString(R.string.enamel_over_title), width / 2f, cy, 29f, colors.text)
        text(c, context.getString(R.string.enamel_result, game.score), width / 2f, cy + 39 * dp, 20f, colors.ink(colors.accent, colors.background))
        text(c, context.getString(R.string.enamel_reward, NeutrinoRewards.enamel(game.score)), width / 2f, cy + 66 * dp, 14f, colors.secondary)
        text(c, context.getString(R.string.enamel_over_hint), width / 2f, cy + 92 * dp, 14f, colors.text)
        replay.set(width * .2f, cy + 113 * dp, width * .8f, cy + 163 * dp)
        p.color = colors.accent; c.drawRoundRect(replay, 10 * dp, 10 * dp, p)
        text(c, context.getString(R.string.enamel_play_again), width / 2f, replay.centerY() + 6 * dp, 17f, colors.onAccent)
    }

    private fun text(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.style = Paint.Style.FILL; p.alpha = 255; p.color = color; p.textSize = size * resources.displayMetrics.scaledDensity
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); p.textAlign = Paint.Align.CENTER
        val measured = p.measureText(text)
        if (measured > width - 24 * dp) p.textSize *= (width - 24 * dp) / measured
        c.drawText(text, x, y, p)
    }

    private fun cancelAim() {
        aiming = false; pointer = -1; power = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun updateAim(event: MotionEvent): Boolean {
        val index = event.findPointerIndex(pointer)
        if (index < 0) { cancelAim(); return false }
        aimX = startX - event.getX(index); aimY = startY - event.getY(index)
        // Même depuis le bas de la base, une traction accessible atteint la pleine puissance.
        val pull = min(min(110 * dp, scale * 2.8f), height - startY - 8 * dp).coerceAtLeast(18 * dp)
        power = (hypot(aimX, aimY) / pull).coerceIn(0f, 1f)
        blocked = !game.isLaunchClear(launchX, launchY) || game.shotSpeed(launchY, aimX, aimY, power) == null
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false
        if (game.over) {
            if (event.actionMasked == MotionEvent.ACTION_UP && replay.contains(event.x, event.y)) { performClick(); reset() }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!game.canLaunch || event.x < left || event.x > left + 10f * scale ||
                    event.y < top + game.baseTop * scale || event.y > top + game.height * scale) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = event.getPointerId(0); startX = event.x; startY = event.y
                launchX = ((event.x - left) / scale).coerceIn(EnamelRules.RADII[game.current], 10f - EnamelRules.RADII[game.current])
                launchY = ((event.y - top) / scale).coerceIn(game.baseTop + EnamelRules.RADII[game.current], game.height - EnamelRules.RADII[game.current])
                blocked = !game.isLaunchClear(launchX, launchY); aiming = true; power = 0f; aimX = 0f; aimY = -1f
            }
            MotionEvent.ACTION_MOVE -> if (aiming) {
                updateAim(event)
            }
            MotionEvent.ACTION_UP -> if (aiming) {
                if (!updateAim(event)) return true
                if (!blocked && game.launch(launchX, launchY, aimX / scale, aimY / scale, power)) {
                    sound?.onDrop(); onStateChanged?.invoke(); performClick()
                } else if (blocked && power >= .025f) { message = context.getString(R.string.enamel_blocked); messageLife = 1.8f }
                cancelAim(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { cancelAim(); parent?.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) cancelAim()
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
