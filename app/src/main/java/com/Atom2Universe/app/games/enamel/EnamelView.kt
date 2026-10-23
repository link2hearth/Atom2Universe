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
    private val labels = resources.getStringArray(R.array.enamel_marks)
    private val art = EnamelPainter(labels)
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
        cancelAim(); launchX = 5f; blocked = false; last = 0L
        onStateChanged?.invoke(); invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = min((w - 32 * dp) / 10.7f, (h - 138 * dp) / 16.7f).coerceAtLeast(1f)
        left = (w - 10 * scale) / 2
        top = 33 * dp + maxOf(0f, (h - 138 * dp - 16.7f * scale) / 2) + .35f * scale
        val bw = (10.7f * scale).toInt().coerceAtLeast(1)
        val bh = (16.7f * scale).toInt().coerceAtLeast(1)
        board?.recycle()
        board = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it); c.scale(scale, scale); c.translate(.35f, .35f); art.board(c)
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
                launchX = game.availableLaunchX(launchX) ?: launchX
                onStateChanged?.invoke()
            }
            if (game.over && !reported) { reported = true; sound?.onGameOver(); onGameOver?.invoke() }
        }
        rings.forEach { it.life -= dt }; rings.removeAll { it.life <= 0f }
        messageLife -= dt
        canvas.drawColor(EnamelPainter.INK)
        text(canvas, context.getString(R.string.enamel_board_label), width / 2f, 20 * dp, 11f, EnamelPainter.GOLD)
        board?.let { canvas.drawBitmap(it, left - .35f * scale, top - .35f * scale, null) }
        canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale)
        for (puck in game.pucks) art.puck(canvas, puck.tier, puck.x, puck.y, puck.radius, puck.angle)
        for (ring in rings) {
            val t = 1 - ring.life / .8f
            val e = ring.event
            p.style = Paint.Style.STROKE; p.strokeWidth = .045f
            p.color = EnamelPainter.COLORS[e.tier]; p.alpha = (ring.life / .8f * 220).toInt()
            canvas.drawCircle(e.x, e.y, EnamelRules.RADII[e.tier] + t * 1.1f, p)
            p.style = Paint.Style.FILL; p.alpha = 255
            p.color = EnamelPainter.INK; p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD; p.textSize = .36f
            canvas.drawText(context.getString(R.string.enamel_merge_points, e.points, e.chain), e.x, e.y - .6f - t, p)
        }
        if (game.canLaunch) {
            if (!aiming) launchX = game.availableLaunchX(launchX) ?: launchX
            val clear = game.isLaunchClear(launchX)
            art.puck(canvas, game.current, launchX, EnamelRules.LAUNCH_Y, EnamelRules.RADII[game.current], alpha = if (clear) 200 else 90)
            if (aiming && power > .025f && clear && aimY < 0) drawAim(canvas)
            p.color = if (clear) EnamelPainter.INK else 0xFFC0503C.toInt()
            p.style = Paint.Style.STROKE; p.strokeWidth = .03f
            canvas.drawCircle(launchX, EnamelRules.LAUNCH_Y, EnamelRules.RADII[game.current] + .14f, p)
            p.style = Paint.Style.FILL
        }
        canvas.restore()
        drawFooter(canvas)
        if (game.over) drawOver(canvas)
        if (active && (game.moving || rings.isNotEmpty() || messageLife > 0f)) postInvalidateOnAnimation()
        else last = 0L
    }

    /** Indique la direction et la distance de freinage, avec les rebonds sur les bandes. */
    private fun drawAim(c: Canvas) {
        val length = hypot(aimX, aimY)
        if (length < .001f) return
        var dx = aimX / length; var dy = aimY / length
        val speed = 1.5f + power * (EnamelRules.MAX_SPEED - 1.5f)
        var distance = speed * speed / (2 * EnamelRules.DRAG)
        var x = launchX; var y = EnamelRules.LAUNCH_Y
        val r = EnamelRules.RADII[game.current]
        line.color = EnamelPainter.INK; line.alpha = 130
        repeat(4) {
            if (distance <= .01f) return@repeat
            val tx = if (dx > .001f) (10 - r - x) / dx else if (dx < -.001f) (r - x) / dx else Float.POSITIVE_INFINITY
            val ty = if (dy > .001f) (16 - r - y) / dy else if (dy < -.001f) (r - y) / dy else Float.POSITIVE_INFINITY
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
        val base = top + 16.35f * scale
        val caption = when {
            messageLife > 0 -> message
            game.over -> context.getString(R.string.enamel_over_hint)
            game.moving -> context.getString(R.string.enamel_moving)
            aiming && blocked -> context.getString(R.string.enamel_blocked)
            aiming -> context.getString(R.string.enamel_power, (power * 100).toInt())
            else -> context.getString(R.string.enamel_controls)
        }
        text(c, caption, width / 2f, base + 21 * dp, 12f, EnamelPainter.CREAM)
        val meterW = min(width * .32f, 140 * dp)
        val mx = width / 2f - meterW / 2
        p.color = 0xFF416162.toInt(); c.drawRoundRect(mx, base + 31 * dp, mx + meterW, base + 35 * dp, 2 * dp, 2 * dp, p)
        p.color = EnamelPainter.GOLD
        c.drawRoundRect(mx, base + 31 * dp, mx + meterW * power, base + 35 * dp, 2 * dp, 2 * dp, p)
        text(c, context.getString(R.string.enamel_next), width - 80 * dp, base + 44 * dp, 10f, EnamelPainter.GOLD)
        art.puck(c, game.next, width - 30 * dp, base + 40 * dp, 11 * dp)
        val unit = min(25 * dp, (width - 32 * dp) / 12)
        val start = (width - unit * 11) / 2
        repeat(12) { tier ->
            art.puck(c, tier, start + tier * unit, base + 62 * dp, unit * .35f, alpha = if (tier <= discoveredTier) 255 else 70)
        }
    }

    private fun drawOver(c: Canvas) {
        p.color = 0xDE173E40.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        val cy = height * .42f
        text(c, context.getString(R.string.enamel_over_title), width / 2f, cy, 29f, EnamelPainter.CREAM)
        text(c, context.getString(R.string.enamel_result, game.score), width / 2f, cy + 39 * dp, 20f, EnamelPainter.GOLD)
        text(c, context.getString(R.string.enamel_reward, NeutrinoRewards.enamel(game.score)), width / 2f, cy + 66 * dp, 14f, EnamelPainter.GOLD)
        text(c, context.getString(R.string.enamel_over_hint), width / 2f, cy + 92 * dp, 13f, EnamelPainter.CREAM)
        replay.set(width * .2f, cy + 113 * dp, width * .8f, cy + 163 * dp)
        p.color = EnamelPainter.GOLD; c.drawRoundRect(replay, 10 * dp, 10 * dp, p)
        text(c, context.getString(R.string.enamel_play_again), width / 2f, replay.centerY() + 6 * dp, 17f, EnamelPainter.INK)
    }

    private fun text(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.style = Paint.Style.FILL; p.alpha = 255; p.color = color; p.textSize = size * resources.displayMetrics.scaledDensity
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); p.textAlign = Paint.Align.CENTER
        val measured = p.measureText(text)
        if (measured > width - 24 * dp) p.textSize *= (width - 24 * dp) / measured
        c.drawText(text, x, y, p)
    }

    private fun cancelAim() { aiming = false; pointer = -1; power = 0f }

    private fun updateAim(event: MotionEvent): Boolean {
        val index = event.findPointerIndex(pointer)
        if (index < 0) { cancelAim(); return false }
        aimX = startX - event.getX(index); aimY = startY - event.getY(index)
        power = (hypot(aimX, aimY) / min(110 * dp, scale * 3.5f)).coerceIn(0f, 1f)
        blocked = !game.isLaunchClear(launchX) || aimY >= -2 * dp
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
                if (!game.canLaunch || event.y < top + 12.8f * scale || event.y > top + 16.3f * scale) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = event.getPointerId(0); startX = event.x; startY = event.y
                launchX = ((event.x - left) / scale).coerceIn(EnamelRules.RADII[game.current], 10f - EnamelRules.RADII[game.current])
                blocked = !game.isLaunchClear(launchX); aiming = true; power = 0f; aimX = 0f; aimY = -1f
            }
            MotionEvent.ACTION_MOVE -> if (aiming) {
                updateAim(event)
            }
            MotionEvent.ACTION_UP -> if (aiming) {
                if (!updateAim(event)) return true
                if (!blocked && game.launch(launchX, aimX / scale, aimY / scale, power)) {
                    sound?.onDrop(); onStateChanged?.invoke(); performClick()
                } else if (blocked) { message = context.getString(R.string.enamel_blocked); messageLife = 1.8f }
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
